# Delivery Status

## Goal

将 `risk-platform` 的认证授权真正接入同级 `auth-platform`，创建本地账号并完成统一身份、前端、
管理后端、交易网关和对象级授权的真实端到端验收。

## State

- Phase: Phase 9 - final audit and handoff
- Status: complete
- Last updated: 2026-07-22
- Acceptance: AC-01 至 AC-12 全部通过

## Completed

- 共享 Casdoor 负责账号、OIDC、角色/权限和机器身份；risk Compose/Helm 不再部署 Casdoor。
- `auth-platform` 新增 risk 专属 schema、幂等开通脚本和强一致 fixture，并以独立 SpiceDB 实例运行。
- 管理 API、决策日志和交易网关改为无状态 JWT resource server，严格校验 issuer、audience、
  owner、sub；权限直接解析统一 Casdoor claim。
- 前端改为 Authorization Code + PKCE，支持安全回调、站内深链、sessionStorage 恢复、续期、
  bearer 请求和 SSO 退出，浏览器不持有 client secret。
- `risk-admin` 使用 vendored `auth-platform-sdk` 写案件 assignee 关系并执行 `work` 判权；403、
  503 与数据库事务回滚补偿均已覆盖。
- 已创建 `risk-e2e-admin` 和隔离验证账号，完成真实 token、API、浏览器、MySQL、SpiceDB、
  Kafka/Elasticsearch 投影验证。
- 双仓库测试、前端质量门、镜像构建、Compose/Helm/脚本/JSON/YAML 校验、文档和 CI 已同步。

## Verification Summary

| Gate | Result |
| --- | --- |
| `auth-platform ./mvnw -B verify` | PASS，117 tests，0 failure/error/skip |
| `risk-platform ./mvnw -B clean verify` | PASS，65 tests；审查修复后再次全量执行 |
| `risk-console` audit/lint/typecheck/unit/build | PASS，0 vulnerabilities，4 unit tests |
| Playwright shared-Casdoor acceptance | PASS，登录/PKCE/规则草稿/案件认领结案/恢复/退出 |
| Real API authorization matrix | PASS，anonymous/machine/human/other-assignee 边界符合预期 |
| Dedicated SpiceDB fixture | PASS，assignee allow、other deny |
| Compose/Helm/scripts/JSON/YAML | PASS |
| Static secret/legacy-auth scan and `git diff --check` | PASS |

## Runtime Evidence

- 统一 Casdoor：`http://localhost:8000`
- 风控控制台：`http://localhost:15173`
- risk authz server：`http://localhost:8212`
- risk 专属 SpiceDB HTTP：`http://localhost:8545`
- 浏览器验收案件：`55c001bc-8090-4d8d-878d-38b9678988d9`，最终 `RESOLVED/FRAUD`
- 人类主体：`cf564f0d-3359-4836-9eae-fe669120d03f`；MySQL assignee、审计 actor 与
  SpiceDB `risk_case#assignee` 一致

## Decisions And Deviations

- 交易热路径只本地验机器 JWT，不增加远程 ReBAC。
- auth SDK 尚无远程制品，因此以可复现的 jar/pom vendoring 接入，并由 CI 检查制品存在。
- 内置 in-app Browser 当时没有可用 browser session；按 browser skill 的故障路径，改用仓库内
  Playwright Chromium 驱动真实页面和共享 Casdoor，未降低断言范围。

## Residual Risks

- SDK 升级仍需从 `auth-platform` 重新构建并刷新 `libs/authz`，后续适合发布到内部 Maven 仓库。
- 跨 MySQL/SpiceDB 没有分布式事务；已做精确回滚补偿，但生产仍建议加入周期性关系对账。
- Vite 构建有大 chunk 警告，不影响功能和安全验收，可单独做前端分包优化。

## Handoff

- 详细审查见 `REVIEW_REPORT.md`。
- 测试证据见 `QA_REPORT.md`。
- 最终范围、上线与回滚见 `DELIVERY_REPORT.md`。
