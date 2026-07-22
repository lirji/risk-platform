# Delivery Report

## Delivered Outcome

`risk-platform` now consumes `auth-platform` as its real authentication and authorization
foundation. It no longer owns a Casdoor instance or a role-to-permission truth table. Shared
Casdoor provides human/machine identity and permissions; risk services validate its JWTs;
`auth-platform-sdk` and a dedicated SpiceDB protect case ownership.

## Architecture After Delivery

```text
Browser -> shared Casdoor (OIDC code + PKCE) -> risk-console -> bearer JWT -> risk-admin
Bank/runtime -> shared Casdoor (client credentials) -> bearer JWT -> fraud-gateway
risk-admin -> auth-platform SDK -> risk authz server -> risk-only SpiceDB
```

The browser/management audience cannot enter the transaction data plane, and machine audiences
cannot enter the management API. Case comment/resolve also requires the caller's verified `sub`
to be the SpiceDB assignee.

## Repository Changes

### auth-platform

- Added `schemas/risk.zed`.
- Added idempotent risk tenant/user/role/permission/machine-client provisioning.
- Added dedicated SpiceDB fixture with allow/deny assertions.
- Added risk onboarding CI checks and updated the README/onboarding guide.

### risk-platform

- Replaced BFF session login with SPA PKCE and stateless bearer APIs.
- Added strict claim validators/mappers to admin, gateway and decision-log services.
- Vendored and integrated auth-platform SDK for case assignment/work checks.
- Added transaction rollback compensation for relationship writes.
- Removed the risk-owned Casdoor deployment; added risk-only SpiceDB and external authz wiring.
- Updated Compose, Helm, Docker/Nginx, OpenAPI, environment examples, CI and operations/security docs.
- Added unit/security tests and credentialed Playwright acceptance coverage.

## Release Readiness

- Local acceptance: ready and verified.
- Production rollout prerequisites: replace all sample values, configure HTTPS issuer and exact
  redirects, create the existing Kubernetes Secret, deploy a risk-dedicated authz server/SpiceDB,
  configure MFA/token TTL/monitoring, and run the same smoke matrix in staging.
- No production deployment or destructive data/account cleanup was performed.

## Rollout

1. Provision the production/staging risk organization, public redirect URIs, roles/permissions and
   separate machine clients in shared auth-platform Casdoor.
2. Deploy the risk-only SpiceDB, write `risk.zed`, run the fixture, then deploy the matching
   auth-platform server with a secret-managed client token.
3. Deploy secure risk backends and verify anonymous/wrong-audience/valid-token matrices.
4. Build the console with the exact public issuer/client/organization and deploy it last.
5. Monitor 401/403, authz 503, compensation error logs and login callback errors.

## Rollback

Roll back risk application images/config and stop the risk authz server if required. Do not delete
the shared Casdoor organization, accounts, SpiceDB database or volumes as part of application
rollback. Those are separate destructive operations requiring explicit approval.

## Evidence Index

- Plan and criteria: `DELIVERY_PLAN.md`
- Current status: `DELIVERY_STATUS.md`
- Review and repaired findings: `REVIEW_REPORT.md`
- QA commands and runtime results: `QA_REPORT.md`
- Operator instructions: `../../security/casdoor.md` and `../../operations/local-development.md`
