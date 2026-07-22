# Auth Platform Integration Delivery Plan

## Requirement

将 `risk-platform` 现有的独立 Casdoor、静态角色权限映射和后端会话登录改为真正消费同级仓库
`auth-platform`：由统一 Casdoor 提供账号、OIDC 和粗粒度权限，由 `auth-platform-sdk ->
auth-platform-server -> risk 专属 SpiceDB` 提供案件对象级判权。随后在 localhost 的统一平台创建
测试账号，使用真实浏览器完成 Casdoor 登录、前端访问、后端授权及业务操作验证。

## Repository Evidence

- `auth-platform/README.md` 将边界定义为：SPA 走 Casdoor OIDC，业务服务走 SDK 调
  `auth-platform-server`，细粒度授权落 SpiceDB。
- `auth-platform/docs/新项目接入指南.md` 要求接入方校验 `sub/owner/iss/aud`、解析
  `permissions[].name`，对象级授权引 SDK，并为每个项目使用独立 SpiceDB 实例。
- `risk-platform/docker-compose.yml` 当前自带 `casdoor` 服务，形成第二套身份基础设施。
- `risk-admin/application.yml` 当前维护 `risk.auth.role-permissions`，角色到权限没有以统一 Casdoor 为真相源。
- `risk-admin` 当前用 OAuth2 Login + server session/CSRF；`risk-console` 没有 PKCE 客户端，回调页也没有处理授权码。
- `CasdoorAuthorityMapper` 把 `permissions` 元素直接 `String.valueOf`，与统一平台实测的对象数组
  `[{name: ...}]` 契约不符；现有配置也没有显式 `aud` 和 `owner` 绑定校验。
- `risk-admin`/`fraud-gateway` 均未依赖 `auth-platform-sdk`；案件虽然在数据库中记录 assignee，
  但评论/结案没有统一授权平台的对象级判权证据。
- 当前 localhost 已运行统一平台 Casdoor `:8000` 和 SpiceDB `:8543`；JDK 21、Node、Docker、
  Compose、curl、jq 均可用。`auth-platform` 工作树干净；`risk-platform` 有既有大量未提交交付改动，必须保留。

## Feasibility

- Verdict: go
- Constraints:
  - 只操作 localhost，不访问或修改生产环境。
  - 不提交真实密码、client secret、SpiceDB key；测试密码只在执行时生成并交付给用户。
  - 保留 `risk-platform` 现有未提交改动，不做 reset、覆盖或无关格式化。
  - 在线交易 P99 路径不能增加逐笔远程 ReBAC 调用；其认证继续本地验统一平台 JWT。
  - `auth-platform-sdk` 尚未发布到公共制品仓库，干净 CI 需要采用仓库已有的 vendored SDK 先例。
- Dependencies:
  - 统一 Casdoor `http://localhost:8000`。
  - risk 专属 PostgreSQL-backed SpiceDB 与独立 `auth-platform-server` 进程。
  - MySQL、Redis、Kafka 等现有 risk 本地栈只启动端到端用到的最小集合。
- Risks and mitigations:
  - OAuth 回调/CORS/CSP 配错：统一使用固定 `/auth/callback`，同步 Casdoor redirect allowlist、Vite、Nginx 和 Helm。
  - 跨租户或跨应用 token 混用：后端同时校验签名、时间、`iss`、精确/派生 `aud` 与固定 `owner=risk-platform`。
  - Casdoor permission 对象解析错误：只接受字符串 permission 或对象的非空 `name`，增加契约测试。
  - SpiceDB 不可用时误放行：对象级授权采用 `disabled/shadow/enforce` 三态；secure/local E2E 使用 `enforce`，依赖故障返回 503。
  - 案件认领与关系元组是跨存储双写：在数据库事务内先完成条件更新，再写 assignee 元组；授权写失败抛错使数据库回滚，数据库状态仍作为最终业务状态校验。
  - SDK 跨仓库依赖：从当前 `auth-platform` 构建产物生成最小 vendored jar/pom，根构建在 initialize 安装，CI 无需第二仓库凭据。

## Product Design

- Actors and goals:
  - 风控管理员：使用统一 Casdoor 账号登录，获得由统一平台签发的权限并访问管理控制台。
  - 风控分析师：只能认领、评论和结案自己认领的案件。
  - 银行/内部服务账号：使用统一 Casdoor 签发的机器 JWT 调用数据面，不依赖浏览器会话。
  - 平台管理员：通过 `auth-platform` 的脚本幂等开通 risk 租户、账号、角色、权限和判权模型。
- Scope:
  - 统一 OIDC Authorization Code + PKCE 登录、恢复、续期和退出。
  - risk 后端资源服务器校验、统一 claim 映射和中央权限门禁。
  - 案件 assignee 的 SpiceDB 对象级 `work` 权限和 SDK 调用。
  - 独立 risk SpiceDB、判权服务运行配置、开通/fixture/smoke 脚本。
  - 新建本地测试账号并完成真实浏览器到后端的功能测试。
- Out of scope:
  - 把规则审批、模型审批等领域状态机迁入 SpiceDB；四眼原则仍由领域模型负责。
  - 在线每笔交易调用远程 ReBAC。
  - 生产部署、生产域名/证书、真实企业账号迁移和提交任何密钥。
- Business rules:
  - 身份主键必须是已验签 token 的 `sub`；租户只取 `owner`，不接受 header/query 覆盖。
  - 角色/权限定义与用户分配以统一 Casdoor 为真相源；risk 仓库不再维护 role-to-permission 表。
  - 前端权限只用于路由和按钮体验，后端 API 是安全权威。
  - 案件 `claim` 仍要求 `case.write`；认领成功后只有对应 `sub` 具备该案件的 `work` ReBAC 权限。
  - 判权依赖故障与无权限分别返回 503 与 403，不得静默放行。

## Acceptance Criteria

| ID | Observable behavior | Priority | Verification |
| --- | --- | --- | --- |
| AC-01 | risk Compose/Helm 不再部署自有 Casdoor，应用配置指向共享 `auth-platform` Casdoor | P0 | Compose/Helm 渲染与配置搜索 |
| AC-02 | 平台脚本可幂等创建 `risk-platform` org、OIDC client、测试用户、角色和 permissions；新 token 的 `sub/owner/aud/permissions` 精确符合契约 | P0 | 真实 Casdoor provision + token claim smoke |
| AC-03 | `risk-console` 使用授权码+PKCE完成登录、回调、深链恢复、会话恢复、续期和单点退出，浏览器不持有 client secret | P0 | 单测 + 真实浏览器 QA |
| AC-04 | `risk-admin` 和 `fraud-gateway` 校验 JWT 签名、时间、issuer、audience、owner；错误 token 返回 401 | P0 | 安全矩阵测试 + localhost curl |
| AC-05 | Casdoor `permissions[].name` 控制前端路由/按钮及后端 API；本地静态 role-permission 映射被移除 | P0 | claim mapper tests + 允许/拒绝 API matrix |
| AC-06 | `risk-admin` 通过 vendored `auth-platform-sdk` 连接 risk 专属判权服务；案件认领写 assignee 元组，非 assignee 的评论/结案为 403，依赖故障为 503 | P0 | 单元/集成测试 + real SpiceDB smoke |
| AC-07 | risk 使用独立 SpiceDB schema/实例，不覆盖 auth-platform 或其他项目 schema；fixture 可重复执行且断言 owner allow/other deny | P0 | schema/fixture smoke |
| AC-08 | 在线交易接口不增加远程 ReBAC；只有正确统一平台机器身份/受众可进入 gateway 受保护端点 | P0 | 代码路径审查 + gateway matrix |
| AC-09 | 使用新账号从浏览器登录后可加载 dashboard，并从规则页面创建一条测试草稿；请求携带 Casdoor bearer token且后端审计 actor 为 token `sub` | P0 | Playwright/浏览器网络与 DB/API 证据 |
| AC-10 | 使用新账号完成案件认领和结案，SpiceDB assignee 与 MySQL assignee 均为同一个 Casdoor `sub` | P0 | 浏览器操作 + DB + SpiceDB 查询 |
| AC-11 | 后端构建测试、前端 lint/typecheck/unit/build、配置校验及新增集成 smoke 全部通过，CI 覆盖这些静态门禁 | P0 | 本地命令日志 + workflow review |
| AC-12 | README、安全手册、本地运行、OpenAPI、部署变量和回滚说明与最终实现一致且不含凭据 | P0 | 文档检查 + secret scan |

## UI/UX Design

- Applicability: required；登录和回调行为改变，业务页面视觉体系保持不变。
- Flow and component map:
  - `/login`：说明统一身份边界，固定展示租户 `risk-platform`；点击后由 `oidc-client-ts` 创建 PKCE state/nonce 并跳转统一 Casdoor。
  - `/auth/callback`：显示加载态，处理 code/state，成功后安全恢复原始站内路径，失败显示可读错误和“重新登录”。
  - 业务路由：启动时先恢复 OIDC session，再调用 `/api/v1/auth/me` 获取后端认可的身份/权限。
  - 退出：调用 `signoutRedirect()`，失败时清理本地 OIDC session 并回登录页。
- State matrix:
  - 登录跳转中按钮禁用；Casdoor 不可达显示重试。
  - 回调处理中显示 loading；state/code/tenant 异常显示 error，不形成重定向循环。
  - 401 尝试一次受控续期并重试；仍失败清理会话并回登录页；403 保持 forbidden 页面。
- Responsive and accessibility behavior:
  - 保留现有响应式登录布局；按钮具备 busy/disabled 状态；错误区域使用 alert 语义；键盘可触发登录和重试。

## Technical Solution

- Chosen approach:
  - 参考 `auth-platform` 已落地的 recsys 模式：Casdoor 提供终端身份/粗粒度权限，消费服务本地验 JWT；管理面对象归属再走 SDK/ReBAC。
  - 单租户 risk 固定使用 shared-app 派生 client id `<base>-org-risk-platform`，同时强校验 `owner=risk-platform`。
  - risk 独立 SpiceDB 只建 `user` 与 `risk_case` 最小模型：`risk_case.assignee -> user`，`work = assignee`。
  - case authz 采用三态守卫；`dev` 默认 disabled，secure E2E/生产配置显式 enforce。
- Alternatives rejected:
  - 保留 BFF session：虽可安全运行，但偏离统一平台现行 SPA PKCE 契约，并继续让 risk-admin 承担登录会话职责。
  - 只改 Casdoor URL：仍保留重复权限映射且没有细粒度判权，不算完整接入。
  - 所有 API 每次远程 ReBAC：重复粗粒度权限且破坏交易热路径延迟目标。
  - 共用 auth-platform 当前 SpiceDB `:8543`：schema/write 是整体替换，会污染其他项目。
- Modules and anticipated file map:
  - `../auth-platform/auth-platform-core/src/main/resources/schemas/risk.zed`：risk 最小 ReBAC schema。
  - `../auth-platform/deploy/risk-platform-provision.sh`：租户、回调、用户、角色、permission 幂等开通与 claim 自检。
  - `../auth-platform/deploy/risk-authz-fixture.sh`：risk schema/关系/判权 smoke。
  - `libs/authz/*`、根 `pom.xml`、`risk-admin/pom.xml`：SDK vendoring 与依赖。
  - `risk-console/src/auth/*`、auth store/API/router/callback/login/tests、package/Docker/Nginx：PKCE 与 bearer token。
  - `risk-admin/.../security/*`：stateless resource server、issuer/aud/owner validators、claim mapper、JWT identity。
  - `risk-admin/.../cases/*`、`risk-admin/.../authz/*`：案件关系写入、对象判权、失败语义。
  - `fraud-gateway/.../security/*`：统一 token validator 与机器身份受众门禁，不引 SDK 热路径。
  - `docker-compose.yml`、`.env.example`、Helm templates/values：外部 Casdoor、独立 authz 依赖与配置。
  - `docs/security/*`、`docs/operations/*`、README/OpenAPI、CI、交付文档。
- Contracts and data:
  - JWT：`iss=http://localhost:8000`（dev）、`aud=<shared-base>-org-risk-platform`、`owner=risk-platform`、`sub=UUID`、`permissions=[{name}]`。
  - API：浏览器统一发送 `Authorization: Bearer <Casdoor access_token>`；不再依赖 Cookie/CSRF。
  - ReBAC：资源 id 为 `risk-platform_<caseId>`，主体为 `user:<Casdoor sub>`。
  - Authz client：`authz.client.server-url`、`authz.client.token`；服务不可用映射 503。
- Security and reliability:
  - PKCE S256、state/nonce、sessionStorage、returnTo 仅允许站内绝对路径。
  - `NimbusJwtDecoder` 使用 JWKS 缓存/轮换，显式 timestamp/issuer/audience/owner validator。
  - header 上限设 64KB；缺失 `sub/owner/aud/permission.name` fail-closed。
  - 不在前端、仓库、日志或交付文档记录 client secret/access token。
- Observability:
  - 记录 authz allow/deny/dependency-failure 计数与结构化日志，不记录 token。
  - 现有审计事件 actor 改为 Casdoor `sub`，E2E 核对。
- Compatibility and migration:
  - `dev` profile 保留本地免登录供离线开发；`secure` profile 切统一平台。
  - 删除/停止使用旧静态 role mapping 与内置 Casdoor；回滚时可恢复上一版本镜像和旧 Compose profile，不迁移业务表。
  - 已认领历史案件可通过一次性 backfill 将 `assignee` 写成 `risk_case#assignee` 关系；本地 E2E fixture覆盖新数据路径。

## Implementation Sequence

1. 平台侧 schema、provision/fixture 脚本与脚本测试，覆盖 AC-02/07。
2. 后端统一 JWT validator/claim mapper，移除静态映射与 BFF session，覆盖 AC-04/05。
3. 前端 PKCE/bearer/callback/logout 改造与组件/纯函数测试，覆盖 AC-03/05。
4. vendored SDK、risk 专属 SpiceDB 配置和案件 authz 守卫/关系双写，覆盖 AC-06/08/10。
5. Compose/Helm/环境变量迁移，移除内置 Casdoor，覆盖 AC-01/07/08。
6. 平台开通新账号，启动最小 secure 栈，执行 curl、真实浏览器和数据库/SpiceDB核验，覆盖 AC-02/04/09/10。
7. 完整 review/repair、回归、文档和 CI 同步，覆盖 AC-11/12。

## Verification Plan

| AC/Risk | Test level | Case or command | Required evidence |
| --- | --- | --- | --- |
| AC-02 | real integration | provision script + password grant claim decode | 无 secret 的 claim 摘要与幂等二次运行 |
| AC-03 | unit/browser | Vitest +真实 Casdoor 登录/刷新/退出 | URL、页面、session 与网络请求 |
| AC-04/05 | backend integration | Spring Security MockMvc +真实 token curl | 200/401/403 矩阵 |
| AC-06/07 | SDK/SpiceDB | fixture、server smoke、case tests | allow/deny/503 与关系查询 |
| AC-08 | review/performance | gateway call graph + security test | 热路径无 AuthzEngine 调用 |
| AC-09 | browser + DB | 登录、dashboard、创建规则草稿 | UI 成功态、API 结果、审计 actor |
| AC-10 | browser + DB + SpiceDB | 认领/结案测试案件 | 两个存储主体一致 |
| AC-11 | build/regression | auth Maven tests；risk Maven verify；npm lint/typecheck/test/build；Compose/Helm/script lint | exit 0 日志 |
| AC-12 | static audit | doc links/config examples/secret search | 无旧架构宣称、无凭据 |

## Documentation Plan

- 更新 risk README、`docs/security/casdoor.md`、`docs/operations/local-development.md`、OpenAPI security 描述和 Helm 运行说明。
- 更新 auth-platform 新项目接入记录，增加 risk schema/provision/smoke 用法。
- 记录账号仅包含用户名/角色/租户；密码只在最终安全交付中给出，不落仓库。

## CI Plan

- risk CI 保持 GitHub Actions，增加 vendored SDK 存在/安装验证、OIDC 前端测试、脚本 shell syntax、Compose/Helm 外部 auth 配置检查。
- auth-platform CI 增加 Maven 全量测试与 risk schema/provision 脚本静态检查；不在 CI 创建外部账号或写共享环境。
- 真实 Casdoor/SpiceDB/浏览器 smoke 仅作为本地验收命令记录，不把测试凭据放进 workflow。

## Rollout And Rollback

- Rollout：先平台开租户/权限和 risk SpiceDB schema；再以 secure 测试环境启动后端；最后构建启用 OIDC 的前端。
- Monitoring：关注 OIDC 401/403、aud/owner 拒绝、authz 503、关系写失败及登录回调错误。
- Rollback：回退 risk 镜像和配置到旧 secure 版本；停止 risk authz server/SpiceDB，不删除 Casdoor org、用户或关系数据。
  删除账号、清库或移除卷属于危险操作，不在本交付中执行。

## Assumptions And Open Decisions

- 假设单租户 organization 名固定为 `risk-platform`，浏览器 client 采用现有 shared application 派生 id。
- 假设测试账号授予 `risk-admin`，用于覆盖所有管理页面和创建规则草稿；不会绕过规则/模型四眼审批。
- 采用案件 assignee 作为首个 ReBAC 对象边界；规则/模型审批仍是领域状态机，而不是本轮扩张为通用资源 ACL。
- 生产 Casdoor HTTPS 域名、真实 secret manager 和用户迁移不阻塞 localhost 验收，但上线前必须另行配置。

## Approval

- Status: approved
- Approved scope: AC-01 至 AC-12 的完整端到端交付范围。
- Evidence: 计划展示后的 active-goal continuation 明确要求继续完成“真正接入 auth-platform、创建账号并自行验证前后端功能测试”。
