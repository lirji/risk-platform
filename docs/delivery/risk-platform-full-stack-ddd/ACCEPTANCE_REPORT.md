# Acceptance Report

> Historical acceptance snapshot. The identity/session statements here were superseded by the
> auth-platform integration delivery and its current acceptance report.

本报告对应已批准交付计划的 30 项验收标准。`Pass` 表示实现与本地自动化证据齐备；`Environment` 表示仓库侧实现完成，但最终验证需要真实外部系统或凭据。

| ID | Status | Evidence |
| --- | --- | --- |
| AC-01 | Pass | Gateway MVC integration test verifies the complete decision response contract. |
| AC-02 | Pass | Direct concurrent service race verifies one Decision, one transaction event, and at most one Case across transactions. |
| AC-03 | Pass | Request validation tests cover fields, enums, amount, and event-time boundaries with stable errors. |
| AC-04 | Pass | Feature/model/rule/persistence/event failure paths are bounded, fail-safe, and instrumented. |
| AC-05 | Pass | DecisionCommitService transaction and Outbox integration tests verify two versioned events and retry/dead states. |
| AC-06 | Pass | Consumer inbox/DLT behavior and unified Outbox/Kafka DLT catalog/replay are integration-tested and exposed in Operations UI. |
| AC-07 | Pass | Shared accumulator parity fixture plus Flink MiniCluster failure/checkpoint restore test. |
| AC-08 | Pass | Event-time, timezone, out-of-order, daily boundary and watermark behavior are fixture-tested. |
| AC-09 | Pass | RuleRelease domain/API/persistence tests cover review, four-eyes approval, source binding, canary, shadow, promotion and rollback. |
| AC-10 | Pass | Source-specific runtime binding, atomic reload and unknown-source fail-safe are engine-tested. |
| AC-11 | Pass | CEP pattern tests cover probe-then-large and failures-then-success signals. |
| AC-12 | Pass | Privacy-safe Elasticsearch projection/search adapter, replay API and Decisions UI are tested. |
| AC-13 | Pass | Case auto-creation, claim, comment, disposition, authoritative label feedback and audit are covered by admin integration tests/UI. |
| AC-14 | Pass | MySQL-to-Hive fact ingestion and deterministic Spark local profile fixture cover RFM, behavior, risk and relationship aggregates. |
| AC-15 | Pass | Versioned tag definition lifecycle and masked profile API/UI are covered by admin context tests. |
| AC-16 | Pass | Rating reliability tests cover atomic lease, retry, `search_after`, batching and individual bulk failure. |
| AC-17 | Pass | Spark fixture verifies deterministic stratified training; metrics include tie-safe AUC, KS, confusion matrix and recall at fixed FPR. |
| AC-18 | Pass | Registry/API/UI tests cover approval, checksum/prefix validation, canary, 100% promotion, rollback, PSI and score distribution. |
| AC-19 | Environment | Debezium connector uses EnvVarConfigProvider, allow-list and versioned topic; JSON is validated. Target database binlog smoke requires deployment credentials. |
| AC-20 | Pass | Independent Vue SPA includes dashboard, decisions, cases, profiles, rules, models, ratings, operations and audit routes. |
| AC-21 | Pass | Shared loading/empty/error/forbidden/retry states, keyboard labels, responsive layouts and desktop/mobile Playwright flows are covered. |
| AC-22 | Environment | Standard OIDC client/resource-server, session/CSRF, callback proxy and route matrix are tested. Real login/logout requires a provisioned Casdoor client. |
| AC-23 | Pass | JWT service permission and fine-grained API authorization matrix tests cover evaluation, publication, case mutation and replay. |
| AC-24 | Pass | Immutable audit paths and privacy tests prevent raw account/payload exposure in Decision/ES/audit APIs. |
| AC-25 | Pass | Prometheus timers/gauges, alerts and provisioned Grafana/Kibana views cover latency, rule/model, lag/DLT, jobs and PSI. |
| AC-26 | Environment | Nacos optional imports/local safe defaults and SkyWalking agents are configured and statically validated; target collector/config-cluster smoke remains. |
| AC-27 | Pass | Compose full/apps/hive and secret-referencing Helm chart pass static render/lint/YAML validation. |
| AC-28 | Pass | GitHub Actions defines backend, frontend, dependency audit, configuration and four image-build gates; local underlying commands and image builds pass. |
| AC-29 | Pass | Feature, engine, gateway and admin ArchUnit suites enforce framework-free domain dependencies. |
| AC-30 | Pass | README, DDD context map, OpenAPI, AsyncAPI, local operation, Casdoor, migration, monitoring, replay and rollback docs are synchronized. |

## Release Boundary

All repository-owned implementation items are complete. AC-19, AC-22 and AC-26 are marked `Environment` because completing those final smoke tests would require creating or changing external systems and injecting credentials; they are not silently represented as locally verified.
