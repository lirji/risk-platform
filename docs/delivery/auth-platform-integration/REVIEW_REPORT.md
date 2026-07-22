# Review Report

## Outcome

- Verdict: approved for the requested localhost integration and acceptance scope.
- Open P0/P1/P2 findings: none.
- Scope reviewed: identity boundaries, claim mapping, object authorization, SPA OIDC flow,
  Compose/Helm, CI, scripts, documentation and secret handling in both repositories.

## Findings Repaired

| Severity | Finding | Repair and evidence |
| --- | --- | --- |
| P0 | 原实现拥有 risk 自己的 Casdoor、静态 role-to-permission 表与 BFF session，不是真正消费 auth-platform | 移除 risk Casdoor 服务；统一 Casdoor 成为真相源；后端改无状态 resource server；前端改 PKCE |
| P1 | Casdoor 实际 `permissions`/`roles` 是对象数组，旧 mapper 会产生错误 authority | 三个 secure 服务按对象 `name` 解析；真实 token 与单测均通过 |
| P1 | 仅验签但未固定 `aud`/`owner` 会允许跨应用或跨租户 token | 管理面、决策日志和网关均加入 issuer/audience/owner/sub fail-closed validator |
| P1 | 案件认领只写数据库，没有统一对象判权；非 assignee 仍可能操作 | SDK 写 `risk_case#assignee`；评论/结案强制 `work`；真实 other=403、assignee=200 |
| P1 | 数据库事务在关系写入后回滚可能遗留孤立 assignee 元组 | 注册事务 after-completion 精确 delete 补偿；新增 rollback 单测 |
| P2 | `.env.example` 的多词 scope 未加引号，按运行手册 source 时会被 shell 拆成命令 | scope 改为带引号的单值；Compose 配置重新验证 |
| P2 | decision-log 原先不具备与管理 API 一致的 token 边界 | 增加 secure resource server、permission 对象解析与边界测试 |

## Security Review

- Authentication: RS256/JWKS，标准 timestamp + exact issuer，明确 audience、owner、sub。
- Authorization: Casdoor permission 为粗粒度权威；案件 assignee 由 auth-platform SDK/ReBAC 权威判定。
- Machine boundary: gateway 仅把预配置机器 audience 映射到 data-plane authority，不信任 scope。
- Browser: Authorization Code + PKCE、state/nonce、sessionStorage、站内 return path；token exchange
  断言包含 `code_verifier` 且不含 `client_secret`。
- Failure behavior: 未登录 401、粗粒度/对象级拒绝 403、判权依赖不可用 503。
- Secrets: 浏览器构建无 secret；真实验收密码/token/key 未写入仓库；Helm 引用 existing Secret。
- Performance: 在线交易路径没有 SDK/远程 ReBAC 调用。

## Maintainability And Operations

- risk schema 被隔离在独立 SpiceDB `:8545`，fixture 明确禁止写共享 `:8543`。
- 开通脚本可重复执行并自行验证人类与机器 token claim。
- vendored SDK 在根 Maven initialize 安装，干净 Docker/CI 构建已验证。
- CI 覆盖双仓库 Maven、前端质量门、配置渲染、镜像构建与 onboarding 静态检查。

## Non-blocking Follow-ups

1. 把 auth SDK 发布到内部 Maven registry，替换 snapshot vendoring。
2. 增加 MySQL assignee 与 SpiceDB tuple 的周期性 reconciliation/告警。
3. 拆分前端 dashboard/vendor chunk，消除 Vite 的 500 kB 警告。
