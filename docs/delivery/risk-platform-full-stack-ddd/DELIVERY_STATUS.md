# Delivery Status

> Historical status snapshot. Authentication and authorization were subsequently migrated to
> auth-platform; this file is not a current runbook.

## Goal

补齐 risk-platform 已确认的全部功能与生产化缺口，新增前后端分离控制台，以 DDD 重构领域边界，并接入 Casdoor 认证授权。

## State

- Phase: Phase 6 — final verification and handoff
- Status: implementation complete; deployment-environment acceptance remains
- Last updated: 2026-07-22

## Delivered

- 建立 12 个 Maven 后端模块及独立 Vue 3/TypeScript 管理控制台，管理面与低延迟决策数据面物理分离。
- 按 Risk Decision、Feature/Profile、Rule Governance、Model Governance、Case Workflow、Rating、Audit/Operations、Identity/Access 限界上下文完成 DDD 分层，并以 ArchUnit 固化依赖方向。
- 决策链路具备契约校验、数据库级并发幂等、保守降级、Decision/Case/Outbox 原子事务、事件版本和端到端指标。
- 实时画像、Flink checkpoint 恢复、CEP、决策日志、统一 Outbox/Kafka DLT、授权重放和审计闭环已交付。
- 规则和模型具备创建/提审/他审/发布、确定性灰度、shadow、100% 晋级、原子热切换和回滚；模型补齐 PSI、分数分布及训练制品指标。
- Decision/Outbox/案件标签可增量写入隐私安全 Hive 事实，Spark 增量画像可投影 Hive/Redis/ES；训练使用复合键、类别权重和确定性分层验证集。
- 评级任务具备原子租约、ES `search_after`、分批 bulk、部分失败识别和重试状态。
- Casdoor 通过标准 OIDC/OAuth2 接入：浏览器 session + CSRF、服务 JWT、细粒度 RBAC、前后端双重权限约束和不可变审计。
- 提供 Nacos、SkyWalking、Prometheus/Grafana、Kibana、Kafka Connect、Docker Compose、Helm、GitHub Actions、OpenAPI/AsyncAPI 和运维文档。

## Verification

| Gate | Result |
| --- | --- |
| `./mvnw clean verify` | pass；13 个 reactor 项、58 项测试全部成功 |
| Flink MiniCluster checkpoint recovery | pass |
| Spark offline-profile local fixture | pass |
| Spark deterministic model training fixture | pass |
| Vue lint / typecheck / Vitest / production build | pass；3 项组件测试，ECharts 6.1 安全升级后重跑 |
| `npm audit --audit-level=moderate` | pass；0 vulnerabilities |
| Playwright desktop + mobile | pass；4 项最新产物流程测试 |
| Compose full/apps/hive, Helm render/lint, JSON/YAML/Bash | pass |
| Four application image builds | pass |

逐项证据和环境验收边界见 [Acceptance Report](ACCEPTANCE_REPORT.md)。

## DDD Audit Conclusion

- `common-feature` 只保留框架无关的特征语义与端口；事件进入 Published Language，Redis 移入适配器。
- `fraud-engine` 保留为核心决策域；Drools、PMML、绑定加载均位于端口之后。
- `fraud-gateway` 已由厚 Controller 重构为入站适配器、应用用例、领域记录和持久化/事件/安全适配器。
- `risk-admin` 采用模块化单体而非过度拆分微服务；上下文间经应用 facade/port 协作，不共享 Controller 或持久化实体。
- Spark/Flink/Kafka/Redis/ES/Casdoor/Nacos 均是外部适配器，不进入领域模型。

## Environment Acceptance Still Required

以下不是仓库实现缺口，需要部署方提供真实外部状态后在目标环境验收：

- 创建实际 Casdoor organization/application/client/role，注入 client secret，并验证登录、退出、MFA、会话超时和服务账号 token。
- 使用目标 MySQL/Kafka/Redis/ES/Hive/MinIO/Nacos/SkyWalking 集群完成容量、故障切换、备份恢复和完整 profile smoke。
- 注入生产证书、密钥、真实脱敏数据源和模型仓库，执行迁移 rehearsal、性能基线和安全扫描。

仓库不包含真实凭据、PII、证书或生产环境变更。
