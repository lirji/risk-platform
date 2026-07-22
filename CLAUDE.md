# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 项目文档以中文为主：总体规划见 `PLAN.md`，能力清单见 `CAPABILITIES.md`，随机森林理论见 `RANDOM-FOREST-THEORY.md`，离线评级 vs 实时拦截两套架构对比见 `ARCHITECTURE-COMPARISON.md`，实时拦截请求链路与引擎内部处理逻辑见 `doc/REQUEST-FLOW.md`，Hive 落地见 `HIVE-INTEGRATION-PLAN.md`。`README.md` 含每条链路的可复制运行命令。

## 技术基线

Java 21 / Spring Boot 3.3.5 / Drools 8.44.2.Final。离线计算用 Spark 4.0，模型经 pmml-sparkml 导出 PMML、由 jpmml-evaluator 线上推理；实时特征有 Kafka consumer 与 Flink DataStream 两个等价实现。Maven 多模块（`com.lrj.risk:risk-platform`），构建用仓库自带的 `./mvnw`（wrapper），勿用系统 mvn。

## 构建与运行

```bash
./mvnw clean package          # 全量构建 + 跑测试（真实编译 DRL 并触发规则断言）
./mvnw -q -pl <module> -am compile   # 单模块编译，-am 连带构建依赖的 common-feature/fraud-engine

# 跑单个测试类 / 方法
./mvnw -pl fraud-engine test -Dtest=ClassName
./mvnw -pl fraud-engine test -Dtest=ClassName#methodName
```

**两类可执行模块，启动方式不同：**

- Spring Boot 服务（`fraud-gateway` 8082、`profiling-realtime`）：`java -jar <module>/target/*.jar`，或 `./mvnw -pl <module> spring-boot:run`。
- Spark / Flink 批或流作业（`fraud-model-train`、`rating-engine`、`profiling-realtime-flink`）：不是 Spring Boot，用 **`./mvnw -q -pl <module> -am exec:exec`** 触发（含 fork JVM 的 Spark/Flink 参数）。改这些模块时记住入口是 exec-maven-plugin 配的 mainClass，不是 jar。

## 本地基础设施

```bash
docker compose up -d                 # 实时栈: MySQL(13307→3306) Redis(6379) Kafka(9092) ES(9200) Kibana(5601) Logstash DolphinScheduler(12345)
docker compose --profile hive up -d  # 离线栈: 额外起 MinIO(9000/9001) + Hive Metastore(9083)，由 ./scripts/setup-hive.sh 统一拉起
docker compose down -v               # 停并清数据卷
```

`scripts/` 下是各链路的一键初始化脚本：`setup-es.sh`（ES 索引模板）、`setup-hive.sh`（MinIO+Metastore 建库灌数）、`setup-rating.sh`（MySQL 配置中心 + ES 标签源）、`retrain.sh`（重训模型，供 DolphinScheduler 调度）。

## 架构要点（跨文件才能看清的部分）

**两套并行架构，共用一套基础设施，理解时不要混淆：**

- **架构 B / 实时拦截**：`fraud-gateway` 同步决策接入层。一笔交易 → 拉特征 → `fraud-engine`（Drools）→ 返回风险等级，**同步返回银行后再异步发 Kafka**。
- **架构 A / 离线评级**：`rating-engine` 任务驱动的 Spark 批作业，配置（模型/规则/阈值/任务）全存 MySQL“改库不改代码”，输入是 Hive 离线宽表 JOIN ES 行为标签，结果写 ES `es-risk-store`。

**Kafka 扇出是数据闭环的核心**：`fraud-gateway` 在 Decision/Outbox 同事务提交后，由 relay 发布 `transaction.v1` 与 `decision.v1`（按账号分区），多个 consumer group 并行消费：
- `profiling-realtime`（group `realtime-feature`）→ 用 Redis 原子操作算窗口特征 → 写回 `feature:{账号}` Hash。
- Logstash（group `logstash-es`）→ 清洗+脱敏（账号掩码 + HMAC 一致性哈希 `acct_hash`）→ 写 ES 按日滚动索引 `txn-YYYY.MM.dd`。
下一笔交易评估时 `fraud-gateway` 读到刚更新的 `feature:{账号}`，特征类规则才会命中——所以特征是“T+ε”的，调试累计/频次规则时要连发多笔。

**Kafka 双 listener（容易踩坑）**：宿主机进程走 `localhost:9092`（PLAINTEXT），docker 网络内容器（如 Logstash）走 `kafka:29092`（INTERNAL）。

**规则引擎结构**：DRL 在 `fraud-engine/.../rules/fraud/`，用 **agenda-group** 把规则分场景集（blacklist / threshold / model），按交易来源绑定要执行的规则集；金额分档用决策表 CSV（`amount-tier.csv`，业务方可维护）。决策聚合 = 取所有命中规则的最高等级，等级→动作映射 LOW=ALLOW / MEDIUM=CHALLENGE / HIGH=REVIEW / REJECT=REJECT。规则必须经 `risk-admin` 的草稿→提审→他人批准→发布流程，管理面用带 `service.runtime.deploy` 的服务 JWT 调 gateway 内部部署接口；不要恢复裸 DRL 公网接口。

**规则 + 模型双轨决策**：`fraud-model-train` 用 Spark 随机森林训练 → 导出 PMML 到 `fraud-engine` 资源目录 `model/fraud-rf.pmml`；`fraud-engine` 内 `ModelScorer`（jpmml-evaluator）每笔算 `fraudScore` 塞进 Drools，与规则在 `model` agenda-group 里共同裁决。改模型特征时，训练侧和 `ModelScorer` 推理侧的特征顺序/口径必须一致。

**安全降级是设计前提**：Redis 不可用返回“特征不可用”并至少 CHALLENGE，规则/模型异常返回更严格的降级决策；Kafka 故障由 Outbox 有界重试并最终标记 DEAD，不能静默丢事件。决策接口受 Sentinel 流控/熔断保护，所有降级必须可计量、可审计，绝不能把未知值当 LOW/ALLOW。

## 共享契约

`platform-contracts` 承载版本化 Kafka/错误 Published Language；`common-feature` 只承载纯 Java `FeatureSnapshot`、`FeatureAccumulator` 与 `FeatureReader` 端口。Redis 适配器位于 gateway，Kafka DTO 位于 contracts。领域包禁止依赖 Spring、Kafka、Redis、JDBC、Drools、PMML 或 Casdoor，ArchUnit 是持续门禁。
