# QA Report

## Result

All twelve acceptance criteria passed on localhost on 2026-07-22. Tests used the shared
auth-platform Casdoor and a risk-only SpiceDB; no production system was contacted.

## Automated Gates

| Area | Command | Result |
| --- | --- | --- |
| auth-platform | `./mvnw -B verify` | PASS，20 suites / 117 tests，0 failures/errors/skips |
| risk backend | `./mvnw -B clean verify` | PASS，13 Reactor modules / 65 tests；final rerun after review repair |
| rollback repair | `./mvnw -B -pl risk-admin -am test` | PASS，risk-admin 20 tests including tuple compensation |
| frontend security | `npm audit --audit-level=moderate` | PASS，0 vulnerabilities |
| frontend static/unit/build | `npm run lint && npm run typecheck && npm test && npm run build` | PASS，3 files / 4 tests；production bundle built |
| config | Compose config + no-Casdoor assertion | PASS |
| Kubernetes | `helm lint` + `helm template` + YAML parse | PASS |
| assets/scripts | JSON parse + `bash -n` | PASS |
| hygiene | both repos `git diff --check` + credential pattern scan | PASS |

All four clean Docker image builds passed: `fraud-gateway`, `risk-admin`,
`fraud-decision-log` and `risk-console`.

## Real Identity And API Matrix

The provision script was run repeatedly to prove idempotency. The resulting human token had:

- issuer `http://localhost:8000`;
- audience `ragshared0client00000001-org-risk-platform`;
- owner `risk-platform`;
- stable subject `cf564f0d-3359-4836-9eae-fe669120d03f`;
- `risk-admin` role and 20 Casdoor permission objects.

Observed API results:

| Request | Expected | Actual |
| --- | --- | --- |
| anonymous -> `/api/v1/auth/me` | 401 | 401 |
| bank machine token -> `/api/v1/auth/me` | 401 | 401 |
| human token -> `/api/v1/auth/me` | 200 | 200，tenant/sub/20 permissions correct |
| human token -> transaction gateway | 401 | 401 |
| bank token -> transaction gateway | 200 | 200，HIGH/REVIEW decision emitted |
| other human -> assigned-case comment | 403 | 403 |
| assignee -> assigned-case comment | 200 | 200 |
| assignee fixture -> SpiceDB `work` | allow | allow |
| other fixture -> SpiceDB `work` | deny | deny |

Kafka projection was verified in Elasticsearch after initializing the decision-log index; the
human decision-log query returned the emitted transaction.

## Browser Acceptance

Runner: Playwright Chromium against `http://localhost:15173` and the real shared Casdoor page.

Passed flows:

1. Redirect from risk login to shared Casdoor and return through `/auth/callback`.
2. Token exchange contained PKCE `code_verifier` and no `client_secret`.
3. Dashboard loaded with the backend-recognized `AUTH PLATFORM` identity.
4. A new rule draft was saved and visible; its audit author was the Casdoor subject.
5. Case `55c001bc-8090-4d8d-878d-38b9678988d9` was claimed and resolved as `FRAUD`.
6. MySQL case assignee, case audit events, label feedback and SpiceDB assignee all matched
   `cf564f0d-3359-4836-9eae-fe669120d03f`.
7. Reload restored the OIDC session; SSO logout returned to `/login`.
8. No browser page errors were observed and all management calls carried bearer tokens.

The in-app Browser had no active browser instance in this environment. Following its documented
fallback path, QA used the repository Playwright runner against the same real services and kept
network-level PKCE/bearer assertions in the test.

## Acceptance Traceability

| AC | Status | Evidence |
| --- | --- | --- |
| AC-01 | PASS | Compose/Helm no Casdoor; shared URLs rendered |
| AC-02 | PASS | idempotent provision + real human/machine claims |
| AC-03 | PASS | unit + browser PKCE/reload/logout |
| AC-04 | PASS | boundary unit tests + 200/401 runtime matrix |
| AC-05 | PASS | object claim parsing; no static role map; UI/API permissions |
| AC-06 | PASS | vendored SDK; real allow/deny; 403/503 and rollback compensation tests |
| AC-07 | PASS | dedicated instance and fixture allow/deny |
| AC-08 | PASS | machine audience gate; gateway call-path review |
| AC-09 | PASS | browser dashboard/rule draft + audit subject |
| AC-10 | PASS | browser claim/resolve + MySQL/SpiceDB identity match |
| AC-11 | PASS | all automated gates and image builds |
| AC-12 | PASS | docs/config/CI synchronized; no delivered credentials in repository |
