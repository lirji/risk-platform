# risk-platform

一个前后端分离、事件驱动的实时交易反欺诈与客户画像平台。同步数据面完成幂等风险决策，异步链路完成实时/离线画像、CEP、决策检索、案件标签回流、模型训练和离线评级；管理面提供规则、模型、案件、画像、评级、运维和审计控制台。

项目已经从“最小演示”重构为 DDD 分层的多模块工程。交付计划与验收标准见 [交付计划](docs/delivery/risk-platform-full-stack-ddd/DELIVERY_PLAN.md)，领域边界见 [DDD Context Map](docs/architecture/ddd-context-map.md)。

## 模块

| 模块 | 职责 |
| --- | --- |
| `platform-contracts` | `transaction.v1`、`decision.v1`、风险信号和稳定错误契约/JSON Schema |
| `common-feature` | 纯 Java 特征值对象、事件时间聚合语义和查询端口 |
| `fraud-engine` | 风险决策领域、Drools 规则适配器、PMML 模型适配器和原子热加载 |
| `fraud-gateway` | 决策数据面：校验、幂等、fail-safe、Decision/Outbox 事务、JWT 资源服务器 |
| `risk-admin` | 管理面模块化单体：统一 JWT 门禁、案件 ReBAC、决策、画像、规则、模型、评级、运维与审计 |
| `risk-console` | React + TypeScript 独立 SPA，覆盖全部管理页面、权限状态与响应式工作流 |
| `profiling-realtime` | 轻量 Kafka 实时画像消费者，Redis Lua 原子更新、inbox、重试/DLT |
| `profiling-realtime-flink` | Flink 等价实时画像，watermark/checkpoint/state TTL/独立 sink |
| `profiling-offline` | Spark 增量离线画像：RFM、行为、风险、关系聚合及 Hive/ES/Redis 投影 |
| `fraud-cep` | Flink CEP：小额试探后大额、多次失败后成功等时序风险信号 |
| `fraud-decision-log` | `decision.v1` 到 Elasticsearch 的幂等投影与检索 API |
| `fraud-model-train` | 案件标签训练、类别不平衡、确定性评估和版本化 PMML 制品 |
| `rating-engine` | 原子租约领取、ES `search_after`、分批 bulk 与单条失败识别的离线评级 |

基础设施适配器位于 `connect/`、`observability/`、`es-init/`、`deploy/helm/` 和 `docker-compose.yml`。

## 核心数据流

```text
银行服务 --JWT--> fraud-gateway --同步--> Decision + Outbox --响应--> 风险动作
                                |
                                +--> transaction.v1 --> 实时画像 / Flink / CEP / Logstash
                                +--> decision.v1 -----> 决策日志 / 案件 / 审计

Decision/Outbox + 案件终态标签 --> Hive 隐私事实 --> 离线画像 / 模型训练 --> 审批 --> 原子模型切换
规则草稿 --> 提审 --> 他人批准 --> 来源发布/灰度 --> 原子规则切换/回滚
```

`sourceId + txnId` 是业务幂等键。Decision 与两条 Outbox 消息同事务提交；消费者按 `metadata.eventId` 去重。Redis、规则或模型不可用时使用保守策略，不把未知风险当成低风险。

## 技术基线

- Java 21、Spring Boot 3.3.5、Spring Security OAuth2/OIDC
- Drools 8.44.2、JPMML、Spark 4、Flink 2
- MySQL 8、Redis 7、Kafka 3.7、Elasticsearch/Kibana 8.13、MinIO/Hive
- React 19、TypeScript、Vite、React Router、TanStack Query、Radix UI、Recharts
- auth-platform（Casdoor + SpiceDB）、Nacos、Prometheus/Grafana、SkyWalking、Docker Compose、Helm

### 大数据引擎全景

Spark 管离线批，Flink 管实时流：

| 引擎 | 模块 | 入口 | 链路 |
| --- | --- | --- | --- |
| Spark | `profiling-offline` | `OfflineProfileJob` | 交易事实 + 案件标签 → 增量画像/训练样本 |
| Spark | `rating-engine` | `RatingEngineJob` | Hive 宽表 JOIN ES 标签 → 评分评级 → ES |
| Spark | `fraud-model-train` | `FraudModelTrainer` | 标签训练 → 指标/manifest/PMML |
| Flink | `profiling-realtime-flink` | `FlinkFeatureJob` | `transaction.v1` → 事件时间特征 → Redis |
| Flink | `fraud-cep` | `FraudCepJob` | `transaction.v1` → 序列模式 → `risk-signal.v1` |
| 原生消费者 | `profiling-realtime` | `TransactionConsumer` | 与 Flink 画像实现二选一，共享聚合语义 |

## 快速开始

### 后端与前端开发模式

```bash
cp .env.example .env
# 替换 .env 中全部占位值
docker compose --env-file .env up -d mysql redis kafka elasticsearch kibana
./mvnw clean verify

# 分别在终端启动
./mvnw -pl fraud-gateway spring-boot:run
./mvnw -pl risk-admin spring-boot:run
cd risk-console && npm ci && npm run dev
```

也可以通过仓库启动器一次启动核心基础设施、两个 API 与控制台。启动器将日志和 PID
写入 `.run/dev`，任一应用异常退出或按下 `Ctrl+C` 时会清理其余应用进程，但会保留
MySQL、Redis、Kafka、Elasticsearch 和 Kibana 容器及数据。它会读取 `.env` 中的
Redis/Kafka 宿主端口覆盖，同时保留 `dev` profile 的 H2 开发数据库：

```bash
# macOS / Linux；start 为默认子命令
./scripts/dev.sh start
./scripts/dev.sh status
./scripts/dev.sh logs
./scripts/dev.sh stop

# Windows
scripts\dev.cmd start
scripts\dev.cmd status
scripts\dev.cmd logs
scripts\dev.cmd stop
```

停止基础设施可另行执行 `docker compose --env-file .env stop`；该命令不删除数据卷。

访问 `http://localhost:5173`。默认 `dev` profile 明确使用本地管理员身份，适合单机开发；共享或生产环境必须使用 `secure`。项目也已加入 `auth-platform/project-portal` 的统一能力目录，本地门户从 `http://localhost:5274` 导航到 `/login?returnTo=%2Fdashboard`，由 Risk Console 自己校验租户并发起 PKCE 登录。

### 试一笔正式契约

金额单位是最小货币单位，时间使用 ISO-8601：

```bash
curl -fsS http://localhost:8082/api/v1/risk/evaluations \
  -H 'Content-Type: application/json' \
  -d '{
    "sourceId":"MOBILE_TRANSFER",
    "txnId":"txn-demo-001",
    "channel":"MOBILE",
    "bizType":"TRANSFER",
    "accountNo":"6225880212340001",
    "counterpartyAccount":"6217000099887766",
    "amount":6000000,
    "currency":"CNY",
    "deviceId":"device-demo",
    "ip":"127.0.0.1",
    "eventTime":"2026-07-22T07:00:00Z",
    "transactionStatus":"UNKNOWN"
  }'
```

响应包含 `decisionId`、`txnId`、`riskLevel`、`action`、`fraudScore`、`hitRules`、`ruleVersion`、`modelVersion` 和完整 `costMs`。相同 `sourceId + txnId` 重试返回首次决策，不重复创建事件或案件。

### 完整 Compose profile

```bash
cp .env.example .env
# 替换 .env 中全部占位值
# 先在 ../auth-platform 幂等开通 risk-platform 租户、账号和机器客户端，
# 再启动 risk 专属 SpiceDB 与 auth-platform-server（详见授权手册）。
docker compose --env-file .env --profile authz up -d risk-spicedb
docker compose --env-file .env --profile full --profile apps up -d --build
```

完整步骤、端口和清理说明见 [本地运行手册](docs/operations/local-development.md)，统一身份与对象授权见 [auth-platform 接入手册](docs/security/casdoor.md)。

## 模型、画像与评级作业

批作业和 Flink/CEP 作业先把 reactor 制品安装到本地 Maven 仓库，再单独启动目标模块，避免依赖模块误执行同一个 main：

```bash
./mvnw -q -DskipTests install
set -a; . ./.env; set +a

# Flink 实时画像（与 profiling-realtime 二选一）
./mvnw -q -pl profiling-realtime-flink exec:exec

# CEP 风险信号
./mvnw -q -pl fraud-cep exec:exec

# Decision/Outbox/案件标签增量写入隐私安全 Hive 事实表
./mvnw -q -pl profiling-offline exec:exec \
  -DofflineMainClass=com.lrj.risk.profiling.offline.RiskFactIngestionJob

# 从事实表增量计算离线画像
./mvnw -q -pl profiling-offline exec:exec

# 案件标签随机森林训练与版本制品
./mvnw -q -pl fraud-model-train exec:exec

# 原子领取一项 PENDING 评级任务
./mvnw -q -pl rating-engine exec:exec
```

Hive/MinIO 初始化由 `scripts/setup-hive.sh` 完成，评级 fixture 由 `scripts/setup-rating.sh` 完成。`FACT_WATERMARK`、`PROFILE_WATERMARK` 和可选的 `PROFILE_AS_OF` 控制可重跑的增量边界；事实表以 `source_id + txn_id` 合并，不持久化账号明文。理论与历史架构对比保留在 [随机森林说明](doc/RANDOM-FOREST-THEORY.md)、[架构对比](doc/ARCHITECTURE-COMPARISON.md) 和 [Hive 集成计划](doc/HIVE-INTEGRATION-PLAN.md)。

离线 Spark 进程要求显式设置 `S3_ACCESS_KEY` / `S3_SECRET_KEY`，评级进程还要求 `MYSQL_PWD`；初始化脚本会从本地 `.env` 导入这些值，仓库不提供固定密码回退。

## 安全与权限

浏览器使用 auth-platform Casdoor 的 OIDC Authorization Code + PKCE，并向后端发送短期 JWT bearer；银行接入和内部发布使用独立的机器 JWT。所有服务强校验签名、时间、issuer、audience、`owner` 和 `sub`，角色/权限只以统一 Casdoor 为真相源。案件评论/结案还通过 `auth-platform-sdk` 校验 assignee 关系；在线交易热路径只做本地 JWT 校验。前端路由/按钮是体验层，后端 API 始终是安全权威。

规则作者不能自审，模型创建者不能自审，发布/回滚/DLT 重放均写审计日志。Compose/Helm 不再部署 risk 自有 Casdoor；risk 使用专属 SpiceDB 保存业务关系，不能与统一平台共享 schema 实例。

生产配置不提交密钥：Compose 从 `.env` 读取，Helm 只引用已有的 `risk-platform-secrets`。账号明文不会进入 Decision/ES/审计日志；在线决策使用 HMAC token，画像查询默认掩码。

## API 与事件契约

- 静态 OpenAPI：[docs/api/openapi.yaml](docs/api/openapi.yaml)
- 静态 AsyncAPI：[docs/api/asyncapi.yaml](docs/api/asyncapi.yaml)
- 运行时 OpenAPI：服务启动后的 `/v3/api-docs` 和 `/swagger-ui.html`
- JSON Schema：`platform-contracts/src/main/resources/schema/`

事件主题为 `transaction.v1`、`decision.v1`、`risk-signal.v1` 及对应 `.DLT`。旧文档中的 `txn-events` 和 `/risk/evaluate` 不再是正式写契约。

## 监控与运维

`GET /actuator/prometheus` 暴露完整 HTTP 直方图、决策分阶段耗时、规则命中、模型分布和 Outbox 计数。`full` profile 预置 Prometheus 告警、Grafana dashboard、Kibana saved objects、Kafka exporter、SkyWalking OAP/UI 和 Nacos 外置可变配置。

故障处理、DLT 重放、规则/模型回滚和备份恢复见 [运维手册](docs/runbooks/operations.md)。

## 质量门禁

```bash
./mvnw clean verify
cd risk-console
npm ci
npm audit --audit-level=moderate
npm run lint
npm run typecheck
npm test
npm run build
```

额外静态校验：

```bash
docker compose --env-file .env.example --profile full --profile apps --profile hive config --quiet
! docker compose --env-file .env.example --profile apps config --services | grep -qx casdoor
helm lint deploy/helm/risk-platform
helm template risk-local deploy/helm/risk-platform >/tmp/risk-platform.yaml
```

GitHub Actions 会运行相同的后端、前端、配置和四个应用镜像构建门禁。
