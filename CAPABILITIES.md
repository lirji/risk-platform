# risk-platform 能力文档

实时交易反欺诈 + 客户画像平台的**已实现能力清单**。每项均已在本机实跑验证。
规划背景见 [PLAN.md](PLAN.md)，运行步骤见 [README.md](README.md)。

- 技术基线：JDK 21 / Spring Boot 3.3.5 / Drools 8.44.2.Final / Spark 4.0 / Flink 2.0.2
- 模块：7 个 Maven 模块（见下），全 reactor `BUILD SUCCESS`，9 个测试通过
- 核心 SLA：同步决策链路目标 P99 ≤ 50ms（warm 调用实测 11~22ms）

---

## 1. 能力总览

```
                          ┌─────────── 共享特征层 (common-feature) ───────────┐
                          │   FeatureSnapshot + FeatureClient(Redis, 降级容错)  │
                          └───────────────────────┬───────────────────────────┘
 银行交易 → fraud-gateway(同步决策 ≤50ms) ──────────┤ 读特征
   │  ① 拉特征  ② 模型评分  ③ 规则引擎  ④ 决策聚合    │
   │  ⑤ Sentinel 限流熔断兜底                         │
   └─ 决策后异步发 Kafka(txn-events) ─┐               │
                                      │               │ 写特征
        ┌─────────────────────────────┼───────────────┴────────────┐
        ▼                             ▼                            ▼
  profiling-realtime            Logstash→ES→Kibana          fraud-model-train
  /profiling-realtime-flink     (清洗+脱敏+检索调查)         (Spark RF→PMML, DS 调度重训)
  (实时特征→Redis, 二选一)
```

| 能力域 | 能力 | 模块 | 状态 |
|--------|------|------|------|
| 决策接入 | 同步风险评估 API (≤50ms) | fraud-gateway | ✅ |
| 规则引擎 | 黑名单/阈值/速度规则 (DRL) | fraud-engine | ✅ |
| 规则引擎 | 决策表 (CSV, 业务方可维护) | fraud-engine | ✅ |
| 规则引擎 | 来源↔规则集绑定 (agenda-group) | fraud-engine | ✅ |
| 规则引擎 | 规则热发布 (不重启) | fraud-engine + gateway | ✅ |
| 模型评分 | 随机森林训练→PMML→线上推理 | fraud-model-train + fraud-engine | ✅ |
| 决策 | 规则+模型双轨裁决 | fraud-engine | ✅ |
| 实时特征 | 轻量消费者 (Spring Kafka + Redis Lua) | profiling-realtime | ✅ |
| 实时特征 | Flink 平替 (DataStream + 键控状态) | profiling-realtime-flink | ✅ |
| 数据接入 | 决策后异步发 Kafka | fraud-gateway | ✅ |
| 检索调查 | Logstash 清洗+脱敏 → ES 滚动索引 | logstash/ + es-init/ | ✅ |
| 检索调查 | Kibana 案件调查 | docker-compose | ✅ |
| 高可用 | Sentinel 限流/熔断/降级 | fraud-gateway | ✅ |
| 可观测 | Prometheus 指标端点 | fraud-gateway | ✅ |
| 运维 | DolphinScheduler 模型重训调度 | docker-compose + scripts | ✅ |

---

## 2. 反欺诈决策能力 (fraud-engine)

### 2.1 规则集 (agenda-group 隔离, 按 sourceId 绑定激活)

| 规则集 | 规则 | 命中码 | 升级 |
|--------|------|--------|------|
| `blacklist` | 黑名单账户 | `BLACKLIST_ACCOUNT` | REJECT |
| `blacklist` | 黑名单设备 | `BLACKLIST_DEVICE` | REJECT |
| `threshold` | 大额转账 (>5万元) | `LARGE_TRANSFER` | HIGH |
| `threshold` | 当日累计超限 (>10万元) | `DAILY_AMOUNT_EXCEEDED` | HIGH |
| `threshold` | 新设备中额 (>1万元) | `NEW_DEVICE_MID_AMOUNT` | MEDIUM |
| `threshold` | 短时高频 (5分钟≥5笔) | `HIGH_VELOCITY` | HIGH |
| `threshold` | 金额分档决策表 (渠道×金额) | `DT_*` | HIGH |
| `model` | 模型分 >0.9 | `MODEL_HIGH_RISK` | REJECT |
| `model` | 模型分 0.6~0.9 | `MODEL_MID_RISK` | HIGH |

- **决策聚合**：取所有命中规则的最高等级；等级→动作 `LOW=ALLOW / MEDIUM=CHALLENGE / HIGH=REVIEW / REJECT=REJECT`
- **来源绑定** (`SourceRuleSetBinding`)：交易带 `sourceId` → 解析绑定的规则集 → 只激活对应 agenda-group。规则"写一次、按来源绑定生效"，不在规则里硬编码渠道条件
- **失控护栏**：`fireAllRules(MAX_FIRES=1000)` 硬上限，防规则失控

### 2.2 决策表 (`rules/fraud/amount-tier.csv`)

业务方用 CSV/Excel 维护的金额分档矩阵，Drools 编译成规则。`DroolsConfig` 程序化加载 DTABLE 资源。

### 2.3 规则热发布

| 端点 | 说明 |
|------|------|
| `POST /rules/reload` (text/plain) | 提交一段 DRL → 重编译 KieContainer → 原子热切换，**不重启**；编译失败返回 400 + 行列号 |

机制：`KieContainerHolder` 持有可热替换容器，进行中的旧 KieSession 不受切换影响（热加载安全）。

---

## 3. 模型能力 (fraud-model-train + ModelScorer)

| 环节 | 实现 |
|------|------|
| 训练 | Spark 4.0 MLlib `RandomForestClassifier`(60树/深6)，特征 `amount/daily_amount/daily_count/hour/cross_bank/device_new` |
| 评估 | `BinaryClassificationEvaluator` AUC (合成数据 ~0.74) |
| 导出 | `pmml-sparkml 3.2.10` → PMML 文件到 fraud-engine 资源目录 |
| 推理 | `jpmml-evaluator 1.7.7` 进程内加载，输出 `probability(1)` 为欺诈分；异常降级返 0 |
| 双轨 | 进引擎前算好 `fraudScore` 塞进 `RiskAssessment`，由 `model` 规则集与规则共同裁决 |

> 训练：`./mvnw -pl fraud-model-train exec:exec`。线上 fraud-engine 启动加载 PMML。
> 选型说明：放弃 MLeap（JDK 21 不兼容），改 Spark+PMML 路线，纯 JVM、微秒级推理。

---

## 4. 特征能力 (画像实时层)

两套实现，**二选一**（不同 consumer group）：

| 实现 | 模块 | 技术 | 特征算子 |
|------|------|------|----------|
| 轻量消费者 | profiling-realtime | Spring Kafka + Redis Lua 原子脚本 | 当日累计金额/笔数(按日重置)、5分钟滑窗计数(ZSET)、新设备(SET) |
| Flink 平替 | profiling-realtime-flink | Flink 2.0 DataStream + 键控状态 | 同上(ValueState 当日累计 + ListState 5分钟滑窗)，嵌入式 MiniCluster |

特征统一写 `feature:{账号}` (Redis Hash)，被反欺诈同步链路 `FeatureClient` 一次取齐读取。

支持的特征键：`blacklist` / `device_blacklist` / `daily_amount`(分) / `daily_count` / `txn_count_5m` / `device_new`。

---

## 5. 数据接入 / 检索调查能力

| 能力 | 实现 |
|------|------|
| 流量解耦 | gateway 决策后异步发 `txn-events` (按账号分区防倾斜)；同步决策不走 Kafka（保 SLA） |
| 清洗 | Logstash filter：时间标准化(`@timestamp`)、金额分→元(`amount_yuan`)、字段裁剪 |
| 脱敏 | 账号掩码(前6后4 `account_masked`) + **HMAC-SHA256 一致性哈希**(`acct_hash`，可聚合可关联但不可逆)，明文移除 |
| 索引 | ES 按日滚动 `txn-YYYY.MM.dd`，mapping 精简(keyword/金额long/best_compression)，模板 `es-init/txn-index-template.json` |
| 调查 | Kibana (localhost:5601)：按 `acct_hash` 关联某账号全部交易、金额区间聚合等 |

> 一条 `txn-events` 流被两个 consumer group 并行消费：`realtime-feature`(算特征) + `logstash-es`(写ES)，互不影响。

---

## 6. 高可用 / 性能能力 (§3.6)

| 能力 | 实现 |
|------|------|
| 低延迟 | 特征预取一次取齐、引擎内零 IO、KieSession 即用即弃、模型进程内推理 |
| 限流 | Sentinel `FlowRule` QPS=20，超出走 `blockHandler` 返回保守决策 `DEGRADED_RATELIMIT` |
| 熔断 | Sentinel `DegradeRule` 慢调用 RT>50ms 占比超阈值熔断 5s，期间走 `fallback` |
| 降级 | Redis 不可用→空特征快照走保守路径；模型异常→退化纯规则；发 Kafka 失败→静默不影响决策 |
| 可观测 | `GET /actuator/prometheus` 暴露指标 |

> 验证：80 并发 → 20 served + 60 快速降级，决策链路不被压垮。

---

## 7. 运维能力

| 能力 | 实现 |
|------|------|
| 本地基础设施 | `docker-compose.yml` 一键起 MySQL/Redis/Kafka/ES/Logstash/Kibana/DolphinScheduler |
| 模型重训调度 | DolphinScheduler standalone (localhost:12345)，Shell 任务定时跑 `scripts/retrain.sh` |
| ES 初始化 | `scripts/setup-es.sh` 应用索引模板 |

---

## 8. REST 接口一览 (fraud-gateway, :8082)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/risk/evaluate` | 同步风险评估，返回 `riskLevel/action/fraudScore/hitRules/costMs` |
| POST | `/rules/reload` | 规则热发布 (text/plain DRL) |
| GET | `/actuator/prometheus` | Prometheus 指标 |
| GET | `/actuator/health` | 健康检查 |

请求示例见 [README.md](README.md)。

---

## 9. 模块清单

| 模块 | 职责 | 产物 |
|------|------|------|
| `common-feature` | 共享特征模型 + Redis 客户端 + Kafka 消息体 | jar |
| `fraud-engine` | Drools 规则引擎 + 模型评分 + 决策聚合 + 热发布 | jar |
| `fraud-gateway` | 同步决策接入层 + 异步发 Kafka + Sentinel | Spring Boot 可执行 jar (:8082) |
| `profiling-realtime` | 实时特征(轻量消费者) | Spring Boot 可执行 jar |
| `profiling-realtime-flink` | 实时特征(Flink 平替) | jar (exec:exec 运行) |
| `fraud-model-train` | Spark 随机森林训练 → PMML | jar (exec:exec 运行) |

---

## 10. 与生产的差距 (已知简化项)

- 训练用合成数据演示管道；生产需 Hive 真实案件标签 + 处理样本不平衡
- 来源↔规则集绑定为内存 Map；生产需 MySQL `t_source_ruleset_bind` + 管理后台
- 规则热发布为 HTTP 直推；生产正解为 KieScanner + KJAR + 审核流
- ES 单节点 replicas=0；生产需 ILM 冷热分层 + 多副本
- 模型热加载未做（重训后需重启 fraud-engine 或调用 reload）
- 未接入注册/配置中心(Nacos)、链路追踪(SkyWalking)、容器编排(K8s)
