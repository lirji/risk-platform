# Risk Platform Full-stack DDD Delivery Plan

> Historical plan. Its BFF session and self-hosted Casdoor decisions were superseded by
> `docs/delivery/auth-platform-integration/DELIVERY_PLAN.md`; use the current security and local
> operation guides for runtime instructions.

## Requirement

将当前“实时反欺诈 + 客户画像”技术演示补齐为前后端分离、可本地完整运行、具备生产演进基础的平台：实现既有审计中确认缺失或半完成的业务、可靠性、安全、模型、数据、运维、测试和文档能力；用 DDD 重新划分领域边界并重构不合理依赖；最后接入 Casdoor 作为统一身份认证与授权平台。

## Repository Evidence

- 当前根 POM 只有 7 个 Maven 模块；规划中的画像管理、离线画像、CEP、规则治理和决策日志模块不存在。
- `fraud-gateway` Controller 同时负责特征读取、DTO 转换、引擎调用和事件发布；领域用例边界缺失。
- `fraud-engine` 的领域模型、Drools、PMML 和 Spring Bean 混在同一包层级，核心策略依赖基础设施实现。
- `SourceRuleSetBinding` 只有无调用方的内存 Map；所有来源实际使用同一默认规则集。
- `TransactionMessage` 没有 `txnId` 或决策快照；Redis/Kafka/Flink 链路没有端到端幂等、DLQ 或恢复证据。
- `profiling-realtime-flink` 没有 `device_new`、checkpoint、水位线和可靠 Redis sink，不等价于轻量消费者。
- `HiveSeedJob` 生成合成数据，当前不存在从实际决策/案件数据生成离线画像和训练样本的生产路径。
- 仓库没有前端、CI、预置 Grafana/Kibana 面板、Casdoor、Nacos、SkyWalking 或 Kubernetes 交付物。
- 当前 `./mvnw clean package` 成功，12 个测试通过；测试只覆盖 `fraud-engine` 和 `rating-engine`。

## Feasibility

- Verdict: conditional-go
- Constraints:
  - 当前工作区有用户未提交的文档移动和新增内容，所有实现必须保留并围绕这些改动工作。
  - Java 21 / Spring Boot 3.3.5 / Drools 8.44.2 / Spark 4 / Flink 2 技术基线保持不变。
  - 真实银行交易、案件标签、三方黑名单、GeoIP 商业库、TLS 证书和生产 Casdoor 租户无法由仓库生成；交付可运行的接口、适配器、迁移、脱敏 fixture 和操作手册，不伪造生产数据或凭据。
  - 只在 localhost 做集成和 UI QA，不部署生产环境。
- Dependencies:
  - MySQL、Redis、Kafka、Elasticsearch、MinIO、Hive Metastore；管理/观测 profile 增加 Casdoor、Prometheus、Grafana、Nacos、SkyWalking。
  - Node.js + npm 用于 Vue 前端；GitHub Actions 作为 CI provider（remote 为 GitHub）。
- Risks and mitigations:
  - 范围大：按可独立验收的纵向切片交付，每片包含契约、实现、测试、状态更新。
  - 热路径被管理功能拖慢：数据面与管理面物理分离；同步路径只保留特征读取、内存决策和一次有界持久化。
  - DDD 过度设计：采用模块化单体管理面，不把每个限界上下文拆成独立服务。
  - Casdoor SDK 版本耦合：使用 Spring Security 标准 OAuth2 Client/Resource Server；Casdoor 仅作为 Identity Provider 适配器。
  - 批流口径漂移：建立版本化 Feature Definition、共享契约测试和 training-serving parity 测试。

## Product Design

- Actors and goals:
  - 银行接入方：幂等提交交易并在有界时间内获得可追踪决策。
  - 风控分析师：搜索决策、查看解释快照、处置 REVIEW/CHALLENGE 案件并回写标签。
  - 规则管理员：管理来源、规则集和规则版本，审核、发布、灰度、回滚。
  - 模型运营：查看模型版本、评估指标和漂移，触发重训、批准上线和回滚。
  - 画像/评级运营：查询客户画像与评级，创建和跟踪离线评级任务。
  - 审计员：只读查看规则、模型、案件和安全操作审计。
  - 平台管理员：管理权限映射、DLQ、重放、运行状态和配置。
- Scope:
  - 补齐画像离线加工/查询/标签元数据、规则治理、CEP、决策日志、案件中心、模型治理、评级任务 API、CDC 配置。
  - 修复幂等、校验、fail-safe、事件时间、Kafka/消费者可靠性、Flink 等价性、模型热加载和批处理并发/分页/部分失败。
  - 新建 Vue 3 前端控制台和管理面 REST API。
  - 接入 Casdoor OIDC/OAuth2，实施 RBAC 和审计。
  - 补齐业务指标、Prometheus/Grafana、Kibana saved objects、SkyWalking、Nacos、Docker/Kubernetes、CI 和文档。
- Out of scope:
  - 提供真实客户/案件/三方数据或生产凭据。
  - 代替银行完成支付执行、短信 OTP 或生产人工客服系统；平台提供挑战/复核工单及回调契约。
  - 生产部署、DNS、真实证书签发和外部 Casdoor 租户创建。
  - 首版引入图数据库；图特征先用窗口关系聚合与离线 Spark 图指标，保留图存储端口。
- Business rules:
  - `txnId + sourceId` 是交易幂等键；重复请求返回首次完成决策，不重复累计特征或创建案件。
  - 每个决策必须具有 `decisionId`、事件时间、特征快照摘要、规则/模型版本、命中明细和审计时间。
  - 特征不可用时不得把未知值当低风险；按来源配置 fail-safe 策略，默认至少 CHALLENGE。
  - 规则版本只有通过审核才能发布；发布失败不改变当前版本；支持灰度、shadow 和回滚。
  - 模型只有通过评估门槛才能激活；热加载原子切换并保留旧版本回滚。
  - REVIEW/CHALLENGE 生成案件；终态处置必须留下操作者、理由、时间和标签回流记录。
  - 批任务领取必须原子化；重复调度不得重复产出或错误覆盖成功结果。

## Acceptance Criteria

| ID | Observable behavior | Priority | Verification |
| --- | --- | --- | --- |
| AC-01 | 合法交易请求返回 `decisionId/txnId/riskLevel/action/fraudScore/hitRules/ruleVersion/modelVersion/costMs` | P0 | Controller integration test + localhost API QA |
| AC-02 | 同一 `sourceId+txnId` 并发或重试只产生一条决策、一次交易事件和至多一个案件 | P0 | concurrency integration test + DB/Kafka assertions |
| AC-03 | 缺字段、非法枚举、非正金额、未来/过旧事件时间返回稳定错误码，不进入引擎 | P0 | validation tests |
| AC-04 | Redis/模型/规则/Kafka/数据库故障分别触发有界、可观测的安全策略；实际响应时间被完整计量 | P0 | failure-injection tests + metrics |
| AC-05 | 决策与 outbox 在同一事务提交；relay 重试后发布版本化 `transaction.v1` 和 `decision.v1` 事件 | P0 | repository/outbox integration tests |
| AC-06 | 消费者按事件 ID 幂等，有限重试后进入 DLT；控制台可查看并授权重放 | P0 | Kafka integration tests + UI QA |
| AC-07 | 轻量与 Flink 实时画像产生相同的 daily/velocity/device/关系特征；Flink 可从 checkpoint 恢复 | P0 | contract fixtures + MiniCluster tests |
| AC-08 | 所有日/小时/窗口/model `hour` 口径使用事件时间和明确时区，乱序/迟到行为可验证 | P0 | clock/watermark tests |
| AC-09 | 来源、规则、规则集、绑定、决策流具有草稿/审核/发布/灰度/shadow/回滚和版本审计 | P0 | domain + API + persistence tests |
| AC-10 | 引擎从已发布版本原子热加载，未知来源按配置拒绝或安全降级，不再统一走隐式默认绑定 | P0 | reload/rollback tests |
| AC-11 | Flink CEP 能识别至少“试探小额后大额”和“多次失败后成功转账”两类序列并输出风险信号 | P1 | Flink CEP tests |
| AC-12 | 决策日志消费者将脱敏快照写 ES，并提供分页搜索、详情和授权回放 API | P0 | ES integration test + API/UI QA |
| AC-13 | REVIEW/CHALLENGE 自动建案，分析师可认领、处置、评论、回写 fraud/normal 标签且全程审计 | P0 | case workflow tests + UI QA |
| AC-14 | 离线画像作业从真实决策/交易事实表增量计算 RFM、偏好、风险和关系标签，写 Hive/ES/Redis | P1 | Spark local integration fixture |
| AC-15 | 标签定义具有口径、类型、时效、版本、负责人和生命周期；画像查询 API 返回值与版本 | P1 | API/persistence tests |
| AC-16 | 评级任务原子领取、可重试、可分页读取 ES、分批写入并识别 bulk 单条失败 | P0 | concurrency and partial-failure tests |
| AC-17 | 模型训练读取案件标签，处理类别不平衡，输出 AUC/KS/混淆矩阵/固定误杀率召回和版本制品 | P1 | deterministic Spark test |
| AC-18 | 模型注册、审批、激活、热切换、灰度、回滚、PSI/分数分布和 training-serving parity 可观察 | P1 | model registry tests + API/UI QA |
| AC-19 | CDC/Kafka Connect 配置能将示例业务表变更发布为版本化事件，敏感配置仅引用环境变量 | P2 | config validation + local smoke |
| AC-20 | Vue 控制台提供仪表盘、决策、案件、画像、规则、模型、评级、运维和审计页面 | P0 | frontend tests + browser QA |
| AC-21 | UI 覆盖 loading/empty/error/forbidden/partial/success/retry，支持键盘、响应式和基本 WCAG AA | P1 | component tests + browser/a11y checks |
| AC-22 | Casdoor 登录、退出和会话恢复可用；后端验证 token/session，角色权限同时约束路由、按钮和 API | P0 | security tests + local Casdoor QA |
| AC-23 | 银行服务账号使用 OAuth2/JWT 权限调用决策 API；规则发布、案件处置、DLT 重放实施细粒度权限 | P0 | authorization matrix tests |
| AC-24 | 安全事件、规则/模型/案件变更和数据重放产生不可变审计记录，敏感字段不写明文日志/ES | P0 | audit/privacy tests |
| AC-25 | Prometheus/Grafana 展示完整请求 P99/P999、分环节耗时、命中率、模型分布、consumer lag、DLQ 和任务状态并有告警规则 | P1 | metrics scrape + dashboard provisioning checks |
| AC-26 | Nacos 外置可变配置，SkyWalking 串联网关/Kafka/管理 API，配置故障有本地安全默认值 | P2 | local ops profile smoke |
| AC-27 | Docker Compose 可一键启动开发/完整 profile；Kubernetes/Helm 清单不含秘密并通过静态验证 | P1 | compose config + helm/kube validation |
| AC-28 | GitHub Actions 运行后端构建测试、前端 lint/typecheck/test/build、配置校验和镜像构建检查 | P0 | local underlying commands + workflow lint |
| AC-29 | DDD 依赖测试证明领域层不依赖 Spring、HTTP、数据库、Kafka、Redis、Drools、PMML 或 Casdoor | P0 | ArchUnit tests |
| AC-30 | README、架构、API、运行、Casdoor、迁移、监控、故障恢复和回滚文档与最终实现一致 | P0 | doc-link/command verification |

## UI/UX Design

- Applicability: required；当前仓库没有 UI 或设计系统。
- Stack: Vue 3 + TypeScript + Vite + Vue Router + Pinia + Element Plus + ECharts；Vitest/Testing Library，Playwright 做关键流程。
- Flow and component map:
  - `/login`、`/auth/callback`、`/403`。
  - 应用壳：可折叠导航、环境标识、全局搜索、用户/角色菜单、通知中心。
  - `/dashboard`：风险趋势、动作分布、规则命中、模型分布、案件 SLA、数据链路健康。
  - `/decisions`：筛选列表 → 决策详情抽屉 → 特征/规则/模型版本 → 授权回放。
  - `/cases`：队列/我的案件 → 详情时间线 → 认领/挑战/复核/结案。
  - `/profiles`：客户搜索 → 实时/离线标签、口径版本、关系摘要、评级。
  - `/rules`：来源/规则集/规则版本 → 编辑 → 测试 → 提审 → 发布/灰度/回滚。
  - `/models`：版本、指标、漂移、重训任务、激活/回滚。
  - `/ratings`：任务创建、进度、失败明细、结果查询。
  - `/operations`：服务健康、consumer lag、DLQ、批任务、审计日志。
- State matrix:
  - 所有数据页统一 skeleton、空态、可重试错误、权限不足和部分数据降级提示。
  - 破坏性/高风险动作使用二次确认、变更理由和权限校验；发布类动作展示版本差异。
  - 长任务采用可刷新状态和可恢复操作，不用无期限阻塞弹窗。
- Responsive and accessibility behavior:
  - ≥1280px 完整侧栏，768–1279px 折叠侧栏，<768px 抽屉导航和卡片化表格重点字段。
  - 可见 focus、完整 label、ARIA 状态、键盘可达、图表同时提供文本摘要，不单靠颜色表达风险。

## Technical Solution

- Chosen approach:
  - 数据面保留独立部署：`fraud-gateway`、实时画像消费者/Flink、CEP、离线 Spark、训练和评级作业。
  - 新增 `risk-admin` 管理面模块化单体，内部按规则治理、画像、案件、模型、评级、运维审计限界上下文分包；提供前端 REST API。
  - 新增 `risk-console` SPA，与 `risk-admin` 前后端分离部署。
  - 新增 `fraud-decision-log`、`fraud-cep`、`profiling-offline`；`profiling-serving` 和 `profiling-tag-mgmt` 的能力合并进 `risk-admin` 的独立 bounded context，避免无价值微服务拆分。
  - `profiling-collector` 以 Kafka Connect/Debezium 部署适配器实现，不引入空 Java 服务。
- DDD bounded contexts:
  - Risk Decision：交易、决策、风险信号、决策策略；核心在 `fraud-engine`，Web/Kafka/DB 为适配器。
  - Feature & Profile：FeatureDefinition、FeatureSnapshot、CustomerProfile；批流实现共享契约而非共享基础设施类。
  - Rule Governance：Source、Rule、RuleSet、Binding、DecisionFlow、RuleRelease。
  - Model Governance：ModelVersion、EvaluationReport、Deployment、DriftReport。
  - Case Workflow：Case、Assignment、Disposition、Comment、LabelFeedback。
  - Rating：RatingTask、ScoreModel、RatingResult。
  - Identity & Access：Principal、Permission、Policy；Casdoor 位于防腐层之外。
  - Audit & Operations：AuditEntry、DeadLetter、ReplayRequest、JobExecution。
- Layering rule per context:
  - `domain`：实体、值对象、聚合、领域服务、领域事件；零框架依赖。
  - `application`：用例与输入/输出端口、事务边界、权限语义。
  - `adapter.in`：REST/Kafka/CLI/Spark/Flink entrypoints。
  - `adapter.out`：MySQL/Redis/Kafka/ES/Hive/MinIO/Drools/PMML/Casdoor。
- Alternatives rejected:
  - 全量微服务：当前团队/代码规模不支持其部署、事务和契约成本。
  - 将所有功能塞进现有 `fraud-gateway`：会污染 50ms 热路径并扩大故障域。
  - 使用 Casdoor Spring Boot starter：其官方 README 仍以 Spring Boot 2.5.x 为示例基线；使用标准 Spring Security OIDC/OAuth2 更适合 Boot 3.3，并保持厂商可替换性。
  - 前端直接保存长期 token：采用后端 OAuth2 client + HttpOnly/SameSite 会话，降低 XSS token 泄漏风险；服务调用仍使用 JWT bearer。
- Modules and anticipated file map:
  - `platform-contracts/`：版本化 transaction/decision/risk-signal 事件及 JSON schema。
  - `common-feature/`：移动为纯领域/应用端口；Redis 适配器迁出或置于 `adapter.out.redis`。
  - `fraud-engine/`：DDD 分层、规则/模型端口、版本化结果、热加载与 parity 测试。
  - `fraud-gateway/`：薄 Web 适配器、应用用例、validation、idempotency、decision/outbox 持久化、OAuth2 resource server、完整 metrics。
  - `profiling-realtime/`、`profiling-realtime-flink/`：共享纯聚合契约、事件时间、幂等/DLT/checkpoint/TTL/关系特征。
  - `fraud-cep/`：Flink CEP 风险信号作业。
  - `fraud-decision-log/`：decision event 消费、ES/Hive projection、DLT。
  - `profiling-offline/`：Spark 增量画像和训练样本加工、Redis/ES projection。
  - `fraud-model-train/`：案件标签、评估、注册制品、版本元数据。
  - `rating-engine/`：原子任务、分页/分批、错误对账。
  - `risk-admin/`：管理面 DDD bounded contexts、REST/OpenAPI、Casdoor、防腐层、Flyway。
  - `risk-console/`：Vue SPA。
  - `db/migration/`、`connect/`、`observability/`、`deploy/helm/`、`casdoor/`、`.github/workflows/ci.yml`。
- Contracts and data:
  - REST `/api/v1/...`；稳定 `code/message/traceId/fieldErrors` 错误格式；游标或页码分页有明确上限。
  - Kafka 事件带 `eventId/schemaVersion/occurredAt/correlationId/sourceId/txnId`；兼容性测试和 schema 文件同库。
  - MySQL 使用 Flyway：决策/outbox、规则治理、模型治理、案件、标签元数据、评级任务、审计、消费幂等表。
  - ES 使用 alias + ILM：交易、决策、画像和审计索引分离；只保存脱敏/哈希字段。
  - Hive 保存事实、离线画像和训练样本；案件终态标签是训练 label 的权威来源。
- Security and reliability:
  - Casdoor OIDC 登录由 Spring Security OAuth2 Client 处理；API 使用 session/CSRF 或 JWT bearer 两种安全链。
  - RBAC 权限：decision.read/replay、case.read/assign/dispose、rule.read/edit/approve/publish、model.read/train/approve/deploy、rating.run、ops.dlq.replay、audit.read、admin.manage。
  - DB 唯一键 + 状态机实现幂等；outbox relay + consumer inbox；重试指数退避，超限 DLT。
  - 所有外部 IO 有连接/读取/总体超时、bulkhead 和指标；默认 fail-safe 可按来源配置。
  - PII 在日志、ES、审计和 UI 默认掩码；密钥只用环境变量/Kubernetes Secret 引用。
- Observability:
  - Micrometer timers/counters/distributions、Kafka lag/DLT、job/model/case 指标；trace/correlation ID 跨 HTTP/Kafka。
  - Prometheus rules + Grafana provisioning；Kibana saved objects；SkyWalking agent/OTel bridge；结构化 JSON 日志。
- Compatibility and migration:
  - 原 `/risk/evaluate` 保留一个兼容窗口，但要求补 `txnId/eventTime`；提供 `/api/v1/risk/evaluations` 正式契约。
  - 旧 `txn-events` 读兼容一版，新事件写 `schemaVersion=1`；消费者先升级、生产者后切换。
  - 数据库采用 expand-and-contract；新表先旁路写入和比对，确认后再作为权威读源。

### Current-module DDD audit

| Current module | Present responsibility and coupling | DDD verdict | Approved refactoring target |
| --- | --- | --- | --- |
| `common-feature` | `FeatureSnapshot`、应用端口 `FeatureClient` 与 Spring/Redis adapter 同模块同层级；同时承载 Kafka message | 必须重构，当前不是清晰 Shared Kernel | 事件迁入 `platform-contracts`；保留 Feature domain/value object 和 port；Redis 实现移到使用方的 `adapter.out.redis`，避免领域包依赖 Spring Data |
| `fraud-engine` | `RiskAssessment`/`TransactionEvent` 领域对象较纯，但 `FraudEngineService` 直接依赖 KIE container、PMML scorer 和内存 binding | 保留模块，重构依赖方向 | 建立 `EvaluateRiskUseCase`、`RuleEvaluationPort`、`ModelScoringPort`、`RuleBindingPort`；Drools/PMML 成为 outbound adapters；版本和值对象进入领域结果 |
| `fraud-gateway` | Controller 直接完成特征查询、对象组装、引擎调用、计时和 Kafka 发布 | 必须重构为薄 inbound adapter | `adapter.in.web` 只做协议/validation；`application` 负责幂等决策用例和事务；MySQL/outbox/Redis/Kafka/security 均通过 ports 接入 |
| `profiling-realtime` | Kafka listener 较薄，但 `RealtimeFeatureService` 把窗口口径、系统时钟和 Redis Lua 混在一起 | 必须抽取可测试领域策略 | 纯 Java `FeatureAccumulator`/`FeatureWindowPolicy` 处理事件时间与状态转换；Spring Kafka/Redis Lua 为 adapters；inbox/DLT 位于 application/infrastructure |
| `profiling-realtime-flink` | Flink `KeyedProcessFunction` 同时维护业务状态并直接创建 Jedis/写 Redis | 必须重构，且要与轻量版共享语义 | Flink adapter 将 keyed state 映射到共享聚合策略；Redis sink 独立；watermark/checkpoint/TTL 属于运行时配置；契约 fixture 验证 parity |
| `fraud-model-train` | 单一 main 直接组建 Spark、训练、评估、写本地文件 | 作为 batch adapter 保留，不建“训练领域实体”假抽象 | 训练 pipeline 作为 application job；数据源、artifact store、model registry 为 ports；Model Governance 聚合在 `risk-admin` |
| `rating-engine` | `RatingModel`/`ScoreRule` 已接近 domain，但 Job 直接 new JDBC/ES client，任务领取非原子 | 局部保留、分层重构 | 保留评分领域对象；新增 `ClaimRatingTaskUseCase` 与 repository/output ports；Spark/JDBC/ES 进入 adapters；任务状态机保护并发与重试 |
| `docker-compose`/scripts/Logstash | 运行和数据投影配置散落，尚无版本化边界或自动验证 | 不做领域化，归 Infrastructure as Code | 按 `deploy/`、`observability/`、`connect/` 分类；用 schema/config tests 约束，不把部署概念放进领域层 |

### Context map and dependency direction

| Upstream context | Contract | Downstream context | Relationship |
| --- | --- | --- | --- |
| Feature & Profile | `FeatureSnapshot` query port + feature definition/version | Risk Decision | Customer/Supplier；Decision 不读取画像存储细节 |
| Rule Governance | `RuleReleasePublished` + immutable rule artifact | Risk Decision | Published Language；Decision 只消费已批准版本 |
| Model Governance | `ModelDeploymentChanged` + PMML artifact metadata | Risk Decision | Published Language；PMML runtime 隔离在 adapter |
| Risk Decision | `DecisionRecorded.v1` | Case Workflow, Profile, Audit & Operations | Domain Event；下游失败不得回滚银行已完成响应 |
| Case Workflow | `CaseDispositionRecorded.v1` | Profile, Model Governance | Domain Event；确认标签是训练 label 权威来源 |
| Feature & Profile | versioned customer profile projection | Rating | Conformist read model；Rating 不回写画像聚合 |
| Rating | `RatingCompleted.v1` | Profile, Case Workflow | Domain Event；高风险评级可生成预警但不直接拒绝实时交易 |
| Identity & Access | principal/permission mapping | all inbound applications | Anti-corruption layer；领域对象不出现 Casdoor SDK 类型或 token claims |

### Aggregate boundaries and invariants

- `Decision` aggregate：以 `DecisionId` 标识，`SourceId + TransactionId` 全局业务唯一；FINAL 后不可改写，只能通过 replay 产生关联的新版本；持久化 Decision 与 Outbox 是同一事务。
- `RuleRelease` aggregate：Draft → Submitted → Approved/Rejected → Published → Retired；提交人不能审批自己的发布；一个 source/ruleset/environment 同时只有一个 active version。
- `ModelDeployment` aggregate：Candidate → Evaluated → Approved → Active/RolledBack；评估不达门槛不可激活；active 指针切换原子且保留上一版本。
- `RiskCase` aggregate：Open → Assigned → Investigating → Disposed；只有 assignee 或 supervisor 可处置；Disposition 必须包含 reason 和 fraud/normal/uncertain label。
- `FeatureDefinition` aggregate：feature key、data type、freshness、owner、event-time policy 和 version 不可分别漂移；已发布版本不可原地编辑。
- `RatingTask` aggregate：Pending → Running → Done/Failed；claim 使用版本/锁原子转换；相同 business date + model version 不重复执行。
- `ReplayRequest` aggregate：必须引用原 decision/event、操作者、理由和目标 consumer；重放创建新审计记录，不删除或修改原消息。

### Refactoring safety rules

- 先添加端口与适配器包装现有行为，再移动实现；每次移动前后运行现有规则/模型测试，避免“大爆炸式”重写。
- 领域包禁止导入 `org.springframework`、`org.kie`、`org.jpmml`、Kafka、Redis、JDBC、HTTP 或 Casdoor 类型；ArchUnit 作为持续门禁。
- 跨 bounded context 不共享 JPA entity、repository 或数据库表对象；只共享 ID/value object、版本化事件和明确 API DTO。
- Spark/Flink/规则引擎是执行技术，不被建模为领域；领域语言使用 Feature、Decision、RuleRelease、ModelDeployment、Case、RatingTask。
- 管理面可在一个进程内，但 context 间调用只经过 application facade/domain event；禁止 Controller 越过用例直接调用 repository。

## Implementation Sequence

1. 基线与架构护栏：契约模块、DDD 包结构、ArchUnit、Flyway、统一错误/时间/ID；覆盖 AC-29/30。
2. 安全决策纵切：新版请求响应、校验、幂等、fail-safe、decision/outbox、完整计时；覆盖 AC-01–05、08。
3. 可靠事件纵切：outbox relay、event schemas、inbox/retry/DLT/replay；覆盖 AC-05/06/24。
4. 实时画像纵切：共享聚合域、轻量/Flink parity、checkpoint/watermark/TTL/关系特征；覆盖 AC-07/08。
5. 规则治理与 CEP：规则管理域、发布/灰度/shadow/回滚、引擎热加载、CEP；覆盖 AC-09–11。
6. 决策检索与案件闭环：decision projection、搜索/详情/回放、案件工作流和标签反馈；覆盖 AC-12/13/24。
7. 离线画像与评级：事实表、增量 Spark 标签、Serving API、评级可靠性；覆盖 AC-14–16。
8. 模型治理：真实标签输入、评估/版本/MinIO、热切换/漂移/parity；覆盖 AC-17/18。
9. 管理面与前端：`risk-admin` APIs、OpenAPI、Vue 页面、状态/accessibility；覆盖 AC-15/20/21。
10. Casdoor 授权：OIDC 登录、服务 JWT、RBAC、审计、前后端权限矩阵；覆盖 AC-22–24。
11. 数据采集与运维：Debezium、ILM/Kibana、Prometheus/Grafana、Nacos/SkyWalking、Docker/Helm；覆盖 AC-19/25–27。
12. 全量 review/repair、localhost QA、文档同步和 GitHub Actions；覆盖 AC-28/30 及全部回归。

## Verification Plan

| AC/Risk | Test level | Case or command | Required evidence |
| --- | --- | --- | --- |
| Backend domain/API | unit, ArchUnit, integration | `./mvnw clean verify` | all modules green, architecture rules enforced |
| Kafka/Redis/MySQL/ES | Testcontainers + localhost | module integration tests and compose smoke | idempotency, outbox/inbox, DLT, partial failure evidence |
| Spark/Flink | local engine tests | Spark local + Flink MiniCluster | deterministic aggregates, checkpoint/recovery, parity |
| Frontend | lint/type/unit/build/e2e | `npm ci && npm run lint && npm run typecheck && npm test && npm run build`; Playwright | route, state, permission and workflow evidence |
| Casdoor | security tests + localhost | OIDC login/logout/session + role matrix | browser/API evidence; no secrets committed |
| Observability/deploy | config/static/smoke | compose config, promtool, dashboard JSON, helm lint/template | valid provisioning and scrape evidence |
| End-to-end | localhost black-box | transaction → decision → events → features/log/case → UI → label → offline/model | case-by-case QA report |

## Documentation Plan

- 修复 README 和 `doc/` 的失效链接、MLeap/PMML、测试数和实现状态矛盾。
- 新增 DDD Context Map、ADR、OpenAPI/AsyncAPI、数据库迁移说明、前端用户手册。
- 新增 Casdoor 配置/RBAC、开发环境、全栈启动、监控告警、DLQ/replay、模型和规则发布、灾备与回滚手册。
- 每个能力只在实际命令或测试证明后标记完成。

## CI Plan

- 使用 GitHub Actions，JDK 21 + Node LTS，缓存 Maven/npm。
- 后端 `./mvnw -B clean verify`；前端 lint/typecheck/unit/build；Docker Compose、shell、JSON/YAML、Prometheus、Helm 静态校验。
- 对需要容器的集成测试使用 CI service/Testcontainers；制品保留测试报告、前端 dist 和构建 jar。
- 不添加部署步骤、生产凭据或宽权限。

## Rollout And Rollback

- 先上线兼容 schema/消费者和旁路 decision log，再启用生产者新事件。
- 规则、模型和来源绑定均以版本化激活指针发布；回滚只切回上一已验证版本。
- 新决策持久化先 shadow 对比；差异指标稳定后启用幂等读路径。
- Casdoor 先保护管理面，再保护决策服务账号；本地 dev profile 保留显式关闭认证的开发入口，生产 profile 禁止关闭。
- 数据迁移只用 Flyway 前向迁移；应用回滚保持向后兼容，不自动删除新列/表。

## Assumptions And Open Decisions

- 假设前端采用 Vue 3/TypeScript/Element Plus；仓库没有既有前端标准可继承。
- 假设管理面采用模块化单体 `risk-admin`，而不是为每个 DDD Context 单独部署微服务。
- 假设 Casdoor 通过标准 OIDC/OAuth2 接入；浏览器采用后端 OAuth2 Client + HttpOnly 会话，银行服务采用 JWT bearer。
- 假设本地使用 Casdoor 内置组织/应用的可重复初始化模板；真实生产 tenant/client/role 由部署方创建并通过环境变量注入。
- 假设真实数据接入只交付适配器、schema 和脱敏 fixture；不生成或提交任何真实 PII。
- 材料风险：完整范围预计产生多个新模块、迁移和基础设施文件，必须按 12 个切片持续执行，不能以一次大提交验证。

## Approval

- Status: approved
- Approved scope: 本计划中的完整前后端分离、DDD 重构、全部 12 个实现切片、质量门禁与 Casdoor 接入范围。
- Evidence: 用户于 2026-07-22 明确回复“批准该计划”。
