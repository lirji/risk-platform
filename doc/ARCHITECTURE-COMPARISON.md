# 架构对比：离线评分评级引擎 vs 实时拦截系统

> **文档状态（2026-07-22）**：保留架构 A/B 的教学对比。当前实时链路使用 `transaction.v1` / `decision.v1` Outbox，管理面与 DDD 边界见 [Context Map](../docs/architecture/ddd-context-map.md)。

本文说明两套架构的分工与拼接关系：

- **架构 A（离线评分评级引擎）**：用户提供的真实架构图，偏**批处理 + 客户画像/评级打标**
- **架构 B（实时拦截系统）**：本仓库 `risk-platform` 已建，偏**实时同步 + 交易反欺诈拦截**

两者**不是替代关系，而是同一平台的两条互补主线**——正是项目最初定义的「实时交易反欺诈 + 客户画像」两个子系统。

---

## 1. 架构 A：离线评分评级引擎（用户真实架构图）

```
离线数据 ┐
        ├──→ Hive 表 (HDFS 文件) ──→ ┌─────────────────────────────────────┐
es数据源 ┘                          │  Spark 评分评级引擎 (YARN 上运行)        │
es集群                              │  ① 自主读取 MySQL 模型及任务配置          │
                                    │  ② 生成并拉取 ES 标签数据                │
配置及模型规则(MySQL) ──任务──────────→│  ③ 评分配置中模型规则 → 应用到标签数据    │
        ▲                           │  ④ 评分结果输出到 ES 库                  │
        │「创建任务」                 └──────────────┬──────────────────────┘
        │                              结果回写 Hive ┘   │ 最终结果
        │                                              ▼
        │                          es 风险库 (标签数据 + 所有维度最终风险结果)
        │                                              │
        │              ┌───────────────────────────────┴──────────────┐
        │              ▼                                               ▼
        └──────── 预警平台 (Tomcat)              客户画像及评分评级平台 (Tomcat)
```

**特征：**
- **批处理 / 任务驱动**：Web 平台「创建任务」写入 MySQL → Spark 引擎读「需要执行的任务」→ 在 YARN 上跑评分作业。非常驻、按任务触发。
- **配置即数据**：模型、评分规则、任务全部存 MySQL，Spark 引擎运行时自主读取——规则/模型变更改库即可，不改代码。
- **ES 为中心**：ES 既是输入（标签数据源），又是输出（es 风险库存标签 + 所有维度最终风险结果）。
- **规则在 Spark 内应用**：评分规则批量作用在「标签数据」上，产出评分/评级（打标），而非逐笔实时裁决。
- **双 Tomcat 服务层**：预警平台（消费风险结果告警）+ 客户画像及评分评级平台（查询画像/评级 + 创建任务）。

---

## 2. 架构 B：实时拦截系统（本仓库 risk-platform 已建）

```
银行交易 → fraud-gateway (同步, P99 ≤ 50ms)
   ① 拉特征(Redis) ② 随机森林评分(PMML) ③ Drools 规则引擎 ④ 决策聚合 ⑤ Sentinel 兜底
   └─ 决策后异步发 Kafka ─→ profiling-realtime/Flink (算实时特征→Redis)
                          └→ Logstash (清洗+脱敏) → ES (检索/案件调查) → Kibana
```

**特征：**
- **实时 / 常驻服务**：银行逐笔调用 REST，硬 SLA 50ms 返回风险等级。
- **Redis 为中心（在线）**：特征预取一次取齐，引擎内零 IO。
- **规则在请求时执行**：Drools 逐笔裁决（放行/复核/拒绝），不是批量打标。
- **ES 为辅（离线检索）**：仅做交易检索 / 案件调查，非实时链路存储。

---

## 3. 逐维度对比

| 维度 | 架构 A（离线评级引擎） | 架构 B（实时拦截，已建） |
|------|----------------------|------------------------|
| 范式 | 批处理，Spark on YARN | 实时同步，常驻服务 |
| 触发 | 平台创建任务 → MySQL → Spark 执行 | 银行逐笔调 REST |
| 时延 | 分钟~小时级（作业） | P99 ≤ 50ms |
| 规则引擎 | 评分规则在 Spark 里应用到标签 | Drools 请求时执行 |
| 模型 | MySQL 配置 + Spark 内打分 | PMML + jpmml 进程内推理 |
| 配置存储 | **MySQL 统一存模型/规则/任务** | 内存绑定 + DRL/PMML 文件 |
| 数据中心 | **ES**（标签源 + 风险库） | **Redis**（在线特征）+ ES（检索） |
| 产出 | 评分/评级（打标，多维风险结果） | 逐笔拦截决策 |
| 服务层 | 预警平台 + 画像评级平台（Tomcat） | REST API |
| 对应项目 | **客户画像 + 评分评级** | **实时交易反欺诈** |

---

## 4. 两者如何拼成完整平台

二者是同一风控平台的**离线层**与**在线层**，通过 ES 和特征/标签衔接：

```
                ┌──────────────── 离线层 (架构 A) ────────────────┐
   离线数据/ES → Hive → Spark 评分评级引擎(任务驱动) → es 风险库(标签+评级)
                                                          │
                                          画像/评级标签下沉为在线特征
                                                          ▼
   银行交易 → ┌──────────────── 在线层 (架构 B) ────────────────┐
             fraud-gateway(50ms) 读 Redis 特征 + Drools + 模型 → 拦截决策
                          │ 决策回流
                          └─→ Kafka → 实时特征/ES 检索 ─→ (反哺离线层样本/标签)
```

**衔接点：**
1. **画像评级 → 实时特征**：架构 A 算出的客户评级/标签（在 ES/Hive）下沉同步到架构 B 的 Redis，成为实时规则/模型的输入特征。
2. **实时决策 → 离线样本**：架构 B 的决策结果/案件标签回流，成为架构 A 评分模型重训的标注样本（PLAN §2.4 冷启动与回流）。
3. **ES 既是 A 的风险库、又是 B 的检索库**：可统一为同一 ES 集群的不同索引。

---

## 5. 架构 A 核心引擎落地状态（rating-engine 模块）

架构 A 的**核心评分评级引擎已落地并实跑验证**（模块 `rating-engine` + `sql/rating-config-schema.sql`），覆盖架构图 Spark 引擎的全部 4 步：

| 能力 | 实现 | 状态 |
|------|------|------|
| 任务驱动评分引擎 | `RatingEngineJob`：领 PENDING 任务 → 加载模型 → Spark 评分评级 → 写 ES → 任务置 DONE | ✅ 已建 |
| MySQL 配置中心 | `t_rating_task`(任务) + `t_score_rule`(评分规则) + `t_rating_grade`(评级阈值)，引擎 JDBC 自主读取 | ✅ 已建 |
| es 风险库 | `es-risk-store` 索引：客户评分 + 评级 + 命中规则 | ✅ 已建 |
| 评分规则在 Spark 应用 | `RatingModel`/`ScoreRule` 随闭包分发到 executor，`parallelize.map` 分布式评分 | ✅ 已建 |
| **Hive 离线宽表（输入源）** | `risk_dw.dwd_cust_feature`（Hive Metastore + MinIO/S3A 存算分离），与 ES 标签 JOIN | ✅ 已建（见 [`HIVE-INTEGRATION-PLAN.md`](HIVE-INTEGRATION-PLAN.md)） |
| 预警平台 / 画像评级平台 | 两个 Tomcat Web UI | ⬜ 未建（纯 UI，验证价值低，本次跳过） |

**实跑验证**（`./scripts/setup-hive.sh` + `./scripts/setup-rating.sh` + `./mvnw -pl rating-engine exec:exec`）：
- 领到 MySQL 任务 `T_DAILY_RATING` + 模型 `RISK_M1`，**Hive 宽表(3行) JOIN ES 行为标签(3行)** 跨源关联
- C001 → 100分 **D级**（全命中5规则）/ C002 → 30分 **B级** / C003 → 0分 **A级**
- 结果写入 `es-risk-store`，任务状态 PENDING→RUNNING→**DONE**（任务驱动闭环）

> 「配置即数据 + 任务驱动」核心已坐实：改 MySQL 规则/阈值即改评分逻辑，不改代码。
> 仅两个 Web 平台未做（纯 UI）；如需可加薄 REST 服务（创建任务 + 查画像评级 + 查预警）代替 Tomcat。

> 现状：架构 B（实时拦截）已 7 模块跑通；架构 A（离线评级）核心引擎 + **Hive 离线宽表输入源（存算分离）均已落地实跑**，仅两个 Tomcat Web UI 未做。第 4 节图中「Hive 表 → Spark」这条已由 `risk_dw.dwd_cust_feature`（Metastore+MinIO/S3A）坐实。

---

## 6. 标签生成放哪里：Spark 离线 vs Flink 实时

「标签生成放 Spark 更好吗？」—— **不是一刀切**。该放哪取决于标签的**时效**和**计算形态**，不是统一答案。架构 A 用 Spark 算离线评级标签是正确的，但它只是标签体系的一层。

### 6.1 按标签类型选引擎

| 标签类型 | 例子 | 放哪 | 原因 |
|----------|------|------|------|
| 离线统计/挖掘类 | 近90天交易笔数、RFM、客户分群、信用评分 | ✅ **Spark** | 全量扫历史、跨表关联、跑模型，正是 Spark 强项 |
| 实时窗口类 | 当日累计、近5分钟频次、当前登录设备 | ❌ Spark 不行 → **Flink/流计算** | 批处理有调度延迟(分钟级)，反欺诈 50ms 等不起 |
| 简单派生/点查 | 年龄段、性别、是否本地 | ⚠️ 不必 Spark | 单表轻量，SQL/普通服务就够，上 Spark 是过度工程 |

### 6.2 Spark 做标签的优势（架构 A 的场景）

1. **海量全量**：扫几亿条历史流水算聚合，分布式天然胜任，单机/SQL 扛不住
2. **跨源关联**：Hive 宽表 + ES 标签 join 一把梭
3. **复杂模型**：聚类、评分模型直接在 Spark MLlib 跑，与标签加工同一引擎
4. **规则批量应用**：评分规则一次作用到全量标签，不逐条调用
5. **可重算/可回溯**：改口径全量重跑，离线层的核心价值

### 6.3 必须警惕的坑

- **别把实时标签塞进 Spark**：最常见错误。「当日累计/5分钟频次」必须流计算（本仓库用 Flink/Redis 做），Spark 批处理延迟会让反欺诈失效
- **training-serving 一致性**：Spark 算的离线标签口径必须与在线实时标签对齐（PLAN §4.2），否则模型线上线下不一致
- **小标签别过度工程**：几个简单维度上 Spark 徒增 YARN 调度与运维成本

### 6.4 结论：标签分层，不是二选一

准确说法不是「Spark 更好」，而是 **「离线标签 Spark 最好，实时标签 Spark 不行」**：

```
离线层(Spark)  →  重型/历史/模型类标签  →  T+1 进 ES/Hive   (架构 A)
实时层(Flink)  →  窗口/频次类标签       →  毫秒进 Redis     (架构 B)
                        ↓
                两层标签口径对齐, 共同喂给评分/反欺诈
```

这正是两套架构互补的根因：架构 A 用 Spark 做离线标签层是对的，架构 B 用 Flink/Redis 做实时标签层也是对的——它们覆盖标签体系的不同时效区间，缺一不可。

---

## 7. 大数据引擎全景：哪个引擎落在哪条链路

§6 讲的是「该用哪种引擎」的理念，本节落到**具体模块**：项目里 Spark 与 Flink 都用了，落位如下。

### 7.1 引擎 → 模块 → 链路对照

| 引擎 | 模块 | 入口类 | 关键依赖 | 落在哪条链路 | 触发方式 |
|------|------|--------|----------|--------------|----------|
| **Spark** | `fraud-model-train` | `FraudModelTrainer` | `spark-mllib` + `pmml-sparkml` + `spark-hive` | 跨链路：随机森林训练 → 导出 PMML 给架构 B 线上推理 | `exec:exec` |
| **Spark** | `rating-engine` | `RatingEngineJob`（+ `HiveSeedJob` 灌数） | `spark-sql` + `spark-hive` | **架构 A** 离线评级核心：Hive 宽表 JOIN ES 标签 → 评级 → 写 ES | `exec:exec` |
| **Flink** | `profiling-realtime-flink` | `FlinkFeatureJob` + `FeatureAggregator` | `flink-streaming-java` + `flink-connector-kafka` | **架构 B** 特征扇出侧：消费 Kafka `txn-events` → 窗口特征 → 回写 Redis | `exec:exec` |
| （非大数据引擎，等价孪生） | `profiling-realtime` | `TransactionConsumer`/`RealtimeFeatureService` | 原生 Kafka consumer + Spring Boot | 同上，与 Flink 版**二选一** | `java -jar` / `spring-boot:run` |

> 这些都不是 Spring Boot 服务，用 `./mvnw -q -pl <module> -am exec:exec` 触发（入口是 exec-maven-plugin 配的 mainClass，不是 jar）。

### 7.2 落位图

```
                 ┌──────────── 架构 A 离线层 ────────────┐
   Hive 宽表 ─┐                                          │
             ├─[Spark] rating-engine (RatingEngineJob) ─→ es 风险库 (评级/标签)
   ES 标签 ──┘                                          │
                                                         │ 标签下沉为在线特征
   历史样本 ─[Spark] fraud-model-train ─→ PMML 模型 ──┐   ▼
              (FraudModelTrainer, 随机森林)          │  Redis
                                                     │   ▲
   银行交易 → ┌──────────── 架构 B 在线层 ────────────┼───┘
             fraud-gateway(50ms) + Drools + PMML 推理 │ ← 模型来自 fraud-model-train
                          │ 决策后异步发 Kafka txn-events
                          ├─[Flink] profiling-realtime-flink ─┐
                          │         (FlinkFeatureJob, 窗口特征) ├─→ 回写 Redis feature:{账号}
                          └─ 或 profiling-realtime(Kafka consumer) ┘  (二选一)
```

### 7.3 三个要点

1. **Spark 横跨两处**：`rating-engine` 在架构 A 主链路上；`fraud-model-train` 不属于任一在线链路，是「离线训练 → 产出 PMML 制品」喂给架构 B 的旁路（详见 [RANDOM-FOREST-THEORY.md](RANDOM-FOREST-THEORY.md)）。
2. **Flink 只在架构 B 的特征扇出侧**，且不在同步关键路径上——它消费决策返回后异步发的 Kafka（详见 [REQUEST-FLOW.md](REQUEST-FLOW.md) §4）。
3. **Flink 版与 Kafka consumer 版是替代关系，不是叠加**：CLAUDE.md 说的「Kafka consumer 与 Flink DataStream 两个等价实现」即指这两个模块，生产二选一。Flink 版用于展示 DataStream 方案，consumer 版更轻量。
