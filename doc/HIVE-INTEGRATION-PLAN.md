# 实施方案：接入 Hive Metastore（MinIO / S3A 存储）

> **文档状态（2026-07-22）**：本文是 Hive 落地设计记录。当前 `profiling-offline` 已实现真实交易事实/案件标签的增量画像，`fraud-model-train` 已改为案件标签、类别权重和版本制品；运行入口以根目录 [README](../README.md) 为准。

> 状态：**已落地并实跑验证通过**（2026-05-30）。本文记录离线层接入 Hive 的实施方案与落地结果。
> 决策前提：项目用途 = **求职/作品展示**；基建重量 = **B 档半真（真 Metastore，不上 HDFS）**；接入范围 = **rating-engine + fraud-model-train 两个模块都接**；存储 = **MinIO / S3A（存算分离）**。
>
> **实跑结果**：`HiveSeedJob` 建 `risk_dw.dwd_cust_feature`/`dwd_fraud_train` 两表（parquet 落 MinIO/S3A）；
> `RatingEngineJob` 经 Hive 宽表 JOIN ES 标签评级 → C001=100/D、C002=30/B、C003=0/A（与改造前一致）；
> `FraudModelTrainer` 从 Hive 读 5000 样本训练 → AUC≈0.74、导出 PMML；fraud-engine 9 个测试全过（PMML 正常加载）。

---

## 0. 背景：现状为什么"没有 Hive"

排查结论：**Hive 此前只存在于架构图与 PLAN 文字中，代码一行未实现**。

- 文档（`ARCHITECTURE-COMPARISON.md` / `PLAN.md` / `README.md` / `CAPABILITIES.md`）把 Hive 列为离线层组件，但仅是规划。
- `FraudModelTrainer.java` 仅有注释「生产改为从 Hive 宽表读」，实际用合成数据。
- `RatingEngineJob` 把架构图里的「Hive 表 → Spark」简化成「**ES 标签 → Spark**」，Spark 也是 `local[*]` 本地模式，未碰 Hive / HDFS / YARN。
- `docker-compose.yml` 无任何 Hive / HDFS 服务。

本方案把 Hive 真正落地到离线层，且不引入 HDFS 的运维重量。

---

## 1. 目标与边界

- **范围**：只动离线层两个模块（`rating-engine`、`fraud-model-train`）+ docker 基建 + seeding 脚本。
- **不动**：实时拦截主链路（`fraud-gateway` / `fraud-engine` / `profiling-realtime(-flink)` / Logstash / Kibana）**一行不改**。实时层本就不需要 Hive。
- **形态**：B 档半真 —— 真 Hive Metastore（管元数据）+ MinIO/S3A（存数据，存算分离），**不上 HDFS / YARN**。Spark 仍在宿主机 `local[*]` 跑。
- **叙事升级**：
  - `rating-engine` 输入从「纯 ES」→「**Hive 宽表 JOIN ES 标签**」，坐实架构图 A 的双输入源 + 跨源关联（呼应 `ARCHITECTURE-COMPARISON.md`「Hive 宽表 + ES 标签 join 一把梭」）。
  - `fraud-model-train` 从「合成数据」→「**从 Hive 训练宽表读**」，training 数据来源真实化。

---

## 2. 基建：docker-compose 新增 3 个服务

| 服务 | 镜像 | 端口 | 作用 |
|------|------|------|------|
| `minio` | `minio/minio` | 9000(S3 API) / 9001(控制台) | 对象存储，账号和密码从 `.env` 注入，挂 volume |
| `minio-init` | `minio/mc`（一次性） | — | 启动后 `mc mb warehouse` 建桶，幂等 |
| `hive-metastore` | `apache/hive:4.0.0`（`SERVICE_NAME=metastore`） | 9083(thrift) | 元数据服务，后端指向已有的 `risk-mysql` |

- **复用 MySQL**：metastore 元数据库用 `risk-mysql` 里新建的 `metastore_db` schema（不另起 DB），靠 JDBC URL 的 `createDatabaseIfNotExist=true` 自动建库；容器启动时 schematool 自动初始化 83 张元数据表。宿主机端口仍 13307，容器内 3306。需把 MySQL 驱动 jar 挂进容器（`setup-hive.sh` 自动备到 `./libs/mysql-connector-j.jar`）。
- **metastore 必须 S3A-aware（实跑修正）**：原设想「metastore 不碰 S3」**不成立** —— Hive `createTable`/`dropDatabase` 会校验并 mkdir 表目录，必须能解析 `s3a://`。修法：`apache/hive:4.0.0` 镜像自带 `hadoop-aws-3.3.6.jar` + `aws-java-sdk-bundle-1.12.367.jar`（在 `tools/lib`，默认不在 classpath），用 `HIVE_AUX_JARS_PATH` 挂上；再挂一份 `hive/conf/core-site.xml`（`fs.s3a.endpoint=http://minio:9000` + path-style + 关 ssl + 密钥）。容器内访问 MinIO 用服务名 `minio:9000`，宿主机 Spark 用 `localhost:9000`。

---

## 3. Spark 侧依赖与配置（rating-engine + fraud-model-train 两个 pom 都加）

新增依赖（已核对）：
- `org.apache.spark:spark-hive_2.13:4.0.0`
- `org.apache.hadoop:hadoop-aws:3.4.1`（对齐 Spark 4.0 自带的 `hadoop-client` 3.4.1，排除其传递的 `hadoop-common` 避免与 shaded client 重复类）
- 传递引入 `software.amazon.awssdk:bundle:2.24.6`（**AWS SDK v2** —— Hadoop 3.4 的 S3A 已从 v1 迁到 v2，不再用 `com.amazonaws:aws-java-sdk-bundle`）

> 注意客户端与服务端 SDK 不同源、各在各自 JVM：**宿主机 Spark（hadoop 3.4.1）用 AWS SDK v2**，**metastore 容器（镜像内 hadoop 3.3.6）用 AWS SDK v1**，互不影响。

两个 Job 的 `SparkSession.builder()` 统一加（已抽成共用工具方法；访问密钥必须来自环境变量，不提供固定回退）：

```
.enableHiveSupport()
.config("spark.sql.catalogImplementation", "hive")
.config("spark.hadoop.hive.metastore.uris", "thrift://localhost:9083")
.config("spark.hadoop.fs.s3a.endpoint", "http://localhost:9000")
.config("spark.hadoop.fs.s3a.access.key", System.getenv("S3_ACCESS_KEY"))
.config("spark.hadoop.fs.s3a.secret.key", System.getenv("S3_SECRET_KEY"))
.config("spark.hadoop.fs.s3a.path.style.access", "true")
.config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
```

---

## 4. 两张 Hive 宽表 schema（EXTERNAL，数据落 s3a）

### `risk_dw.dwd_cust_feature`（rating-engine 评级输入，离线重型标签）

| 列 | 类别 | 说明 |
|---|---|---|
| `cust_id` | 主键 | 客户号 |
| `txn_cnt_30d` / `txn_cnt_90d` | RFM-F | 近30/90天交易笔数 |
| `txn_amt_30d` / `txn_amt_90d` | RFM-M | 近30/90天交易金额 |
| `avg_amt` / `max_amt` | RFM-M | 笔均 / 最大单笔 |
| `recency_days` | RFM-R | 最近交易距今天数 |
| `night_txn_ratio` | 行为偏好 | 夜间交易占比 |
| `cross_bank_ratio` | 资金/关系 | 跨行交易占比 |
| `counterparty_cnt` | 关系网络 | 对手方数量 |
| `device_cnt` | 设备 | 常用设备数 |
| `account_age_days` | 生命周期 | 账龄 |

> 该表 JOIN 已有 ES `cust-tags` 实时标签 → Spark 评分 → A/B/C/D 评级。

### `risk_dw.dwd_fraud_train`（model-train 训练样本）

列：`amount, daily_amount, daily_count, hour, cross_bank, device_new, label`

> **列名严格等于** `FraudModelTrainer.FEATURE_COLS` + `label`，保持 training-serving 对齐（与在线 `ModelScorer` 字段一一对应）。

---

## 5. 新增 seeding

### `HiveSeedJob`（放 rating-engine）
- 用 Spark `createDataFrame(...).write.option("path", "s3a://warehouse/...").saveAsTable("risk_dw.dwd_cust_feature" / "dwd_fraud_train")` 建库建表灌数。
- 训练表合成逻辑直接搬现有 `FraudModelTrainer.synthesize()`：数据照旧合成，只是落点从内存变成 Hive 表 —— 正是「生产改读 Hive」的落地形态。

### `scripts/setup-hive.sh`
- 等 minio / metastore 就绪 → 在 `risk-mysql` 建 `metastore_db` → 跑 `HiveSeedJob`。
- 与现有 `setup-rating.sh`（灌 MySQL 配置 + ES `cust-tags`）配合，**ES 标签源保留**。

---

## 6. 改造两个 Job

### `RatingEngineJob`（仅步骤 2 改造，其余不变）
- 原 `es.fetchAll(sourceIndex)` → 改为：`spark.table("risk_dw.dwd_cust_feature")`（Hive 宽表）与 ES `cust-tags`（用 `EsClient.fetchAll` 结果建临时 DataFrame/视图）**在 Spark 里 JOIN on cust_id**。
- join 后每行转 `Map<String,Double>` 喂给**现有 `RatingModel.score()`**。
- 评分/评级/写 ES 风险库逻辑、任务状态机（PENDING→RUNNING→DONE）**全部不变**。

### `FraudModelTrainer`
- `synthesize(5000)` → 改为 `spark.table("risk_dw.dwd_fraud_train")`。
- 其余（VectorAssembler / RandomForestClassifier / AUC 评估 / 导出 PMML 到 fraud-engine 资源目录）**不变**。

---

## 7. 文档更新
`ARCHITECTURE-COMPARISON.md`、`README.md`、`CAPABILITIES.md`、`PLAN.md`：把 Hive 从「规划态」改为「已落地」，补 MinIO/S3A 存算分离说明与启动步骤。

---

## 8. 验证（DoD）

1. `docker compose up -d minio minio-init hive-metastore mysql elasticsearch` 全部健康。
2. `./scripts/setup-hive.sh` → 两张 Hive 表建成、s3a 上有 parquet、`SHOW TABLES IN risk_dw` 可见。
3. `./mvnw -pl rating-engine exec:exec` → Hive JOIN ES 跑通，C001→D / C002→B / C003→A 评级**与现状一致**，结果写入 ES 风险库。
4. `./mvnw -pl fraud-model-train exec:exec` → 从 Hive 读样本训练，打印 AUC、导出 PMML。
5. 实时链路冒烟一遍，确认未受影响。

---

## 9. 主要风险（落地结果）

- **R1 hadoop-aws / aws-sdk 版本** —— ✅ 已解。Spark 4.0 自带 hadoop-client 3.4.1 → 用 `hadoop-aws:3.4.1`，传递引入 **AWS SDK v2** `software.amazon.awssdk:bundle:2.24.6`；排除 hadoop-aws 带的 hadoop-common 避免与 shaded client 重复类。Spark↔MinIO 的 S3A 读写一次跑通。
- **R2 hive-metastore 初始化 + S3A** —— ✅ 已解，但比预想多一步。schematool 自动初始化 `metastore_db`（83 表）顺利；**但 metastore 建表/删库会 mkdir 表目录，必须 S3A-aware**：用 `HIVE_AUX_JARS_PATH` 挂镜像自带的 `hadoop-aws-3.3.6`+`aws-java-sdk-bundle-1.12.367`，再挂 `hive/conf/core-site.xml`（指向 `minio:9000`）后建表成功。
- **R3 Spark4 + servlet/jackson 冲突** —— ✅ 未复发。沿用已有约束（pom 锁 servlet 5.0.0 + jackson-bom 2.18.2），新依赖未引入新冲突。

> 一处设计调整：原计划「EXTERNAL 表把 S3 挡在 Spark 侧、metastore 不碰 S3」被实跑推翻（见 R2）。最终改为 metastore 也具备 S3A + `spark.sql.warehouse.dir=s3a://warehouse`，db/表 location 统一落 s3a；`HiveSeedJob` 用 `DROP DATABASE ... CASCADE` + `CREATE DATABASE ... LOCATION 's3a://...'` 保证幂等重灌。

---

## 10. 落地顺序建议

1. docker-compose 加 minio + minio-init + hive-metastore，打通 metastore↔MySQL（先解 R2）。
2. 两个 pom 加依赖，验证 Spark↔MinIO 的 S3A 读写（先解 R1）。
3. 写 `HiveSeedJob` + `setup-hive.sh`，建表灌数。
4. 改 `RatingEngineJob`（Hive JOIN ES）。
5. 改 `FraudModelTrainer`（读 Hive 训练表）。
6. 更新文档，跑完整 DoD。
