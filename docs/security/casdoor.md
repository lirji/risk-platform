# auth-platform identity and authorization

`risk-platform` does not deploy or own a Casdoor instance. Human and machine identities are
provisioned in the sibling `auth-platform` repository and all secure services validate tokens
issued by that shared Casdoor. Risk business relationships use a dedicated SpiceDB data plane;
they must never be written to auth-platform's shared SpiceDB instance.

## Provisioning

With auth-platform Casdoor running at `http://localhost:8000`, run the idempotent onboarding
script from the `auth-platform` repository. Supply secrets only through the process environment:

```bash
cd ../auth-platform
RISK_USER=risk-local-admin \
PASSWORD='<strong-local-password>' \
BANK_CLIENT_SECRET='<bank-client-secret>' \
RUNTIME_CLIENT_SECRET='<runtime-client-secret>' \
CASDOOR_ADMIN_PW='<current-admin-password>' \
./deploy/risk-platform-provision.sh
```

The script creates/updates:

- organization `risk-platform`;
- public browser client `ragshared0client00000001-org-risk-platform` with `/auth/callback`
  redirect URIs and Authorization Code + PKCE support;
- the requested user, five roles and the full permission set;
- machine clients `risk-bank-client` and `risk-runtime-client`;
- real password/client-credentials token checks for `iss`, `aud`, `owner`, `sub` and permissions.

It is safe to rerun and does not print tokens or secrets. No browser client secret exists or belongs
in `.env`.

## Runtime contract

The SPA uses `oidc-client-ts`, Authorization Code + PKCE S256, `state`/`nonce`, session storage and
the exact callback `/auth/callback`. It sends a bearer access token to the management API. On one
401 it attempts one controlled silent renewal; a second failure clears the local OIDC session.
The public `/login` route asks the user to confirm the Casdoor organization before redirecting. It
accepts only the configured `risk-platform` organization and verifies that the public client ID
uses the matching `-org-risk-platform` suffix; unknown tenants and configuration drift fail closed
without starting OIDC. The unified project portal links to
`/login?returnTo=%2Fdashboard`, so PKCE state is always created by the Risk Console origin.

`risk-admin` and `fraud-decision-log` accept only human tokens with:

- a valid signature, timestamps and exact configured issuer;
- audience `ragshared0client00000001-org-risk-platform`;
- `owner=risk-platform` and a non-empty `sub`;
- permissions read from either strings or Casdoor objects such as `{"name":"case.read"}`.

`fraud-gateway` accepts only the machine audiences `risk-bank-client` and
`risk-runtime-client`. The audience grants the data-plane authority; arbitrary caller-supplied
scope text is not trusted. The online evaluation path performs local JWT validation only and has
no remote ReBAC call.

## Object authorization

Case assignment is the first object-level boundary. The risk-specific schema is
`auth-platform-core/src/main/resources/schemas/risk.zed` in the auth-platform repository:

```text
risk_case:<tenant>_<caseId>#assignee@user:<Casdoor sub>
```

After a successful claim, `risk-admin` writes that relationship through the vendored
`auth-platform-sdk`. Comment and resolve operations require `risk_case#work`; a different subject
receives 403 and an unavailable authorization dependency receives 503. Secure deployments use
`RISK_AUTHZ_MODE=enforce`; local JVM `dev` defaults to `disabled`.

The relationship write participates in the surrounding case workflow through rollback
compensation: if the database transaction does not commit, `risk-admin` deletes the exact
assignee tuple it just wrote. A failed compensation is logged as an error and should be handled by
an operational reconciliation job in a production deployment.

The dedicated local schema smoke test is:

```bash
cd ../auth-platform
SPICEDB_HTTP=http://localhost:8545 SPICEDB_KEY='<risk-key>' APPLY=1 \
  ./deploy/risk-authz-fixture.sh
```

Never point this command at shared SpiceDB port `8543`, because schema writes replace the target
instance's schema.

## Permission matrix

| Capability | Read | Mutate / high risk |
| --- | --- | --- |
| Decisions | `decision.read` | `decision.replay` |
| Cases | `case.read` | `case.write` plus object-level `work` |
| Profiles/tags | `profile.read` | `profile.write` |
| Rules | `rule.read` | `rule.write`, `rule.approve`, `rule.publish` |
| Models | `model.read` | `model.write`, `model.approve`, `model.activate` |
| Rating | `rating.read` | `rating.write` |
| Operations | `ops.read` | `ops.replay` |
| Audit | `audit.read` | append-only application writes |
| Data plane | none | audience-bound `risk.evaluate`, `service.runtime.deploy` |

Casdoor is the role/permission source of truth; the risk repository contains no static
role-to-permission expansion table. Frontend checks are for usability and backend checks remain
authoritative.

## Production and rollback

- Use HTTPS and keep issuer/public URL stable; use the internal URL only for server-side JWKS and
  token requests.
- Keep service client secrets, `AUTHZ_CLIENT_TOKEN`, database passwords and SpiceDB keys in a
  secret manager/Kubernetes Secret.
- Build the console image with production `VITE_CASDOOR_*` values and set its runtime
  `CASDOOR_PUBLIC_URL` for CSP.
- Configure MFA and short token TTLs for privileged roles; preserve separate authors/reviewers.
- Rollback uses the previous risk images/config. Stopping the risk authz service is reversible;
  account deletion, database cleanup and volume deletion are intentionally separate operations.
