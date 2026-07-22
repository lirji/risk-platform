# Local development and auth-platform startup

## Lightweight development

The default `dev` profile keeps the explicit local administrator identity and does not require
auth-platform. It is for a single developer machine only:

```bash
cp .env.example .env
# Replace every placeholder before continuing.
docker compose --env-file .env up -d mysql redis kafka elasticsearch kibana
./mvnw clean verify
./mvnw -pl fraud-gateway spring-boot:run
./mvnw -pl risk-admin spring-boot:run
cd risk-console && npm ci && npm run dev
```

The console is `http://localhost:5173`, the transaction API is
`http://localhost:8082/api/v1/risk/evaluations`, and the management API is `:8083`.

## Secure end-to-end environment

The secure profile consumes auth-platform Casdoor at `:8000`; it never starts a local Casdoor.

1. Start auth-platform infrastructure and provision risk identities as described in
   [the identity guide](../security/casdoor.md).
2. Fill `.env` from `.env.example`. Keep all secret values local.
3. Start the dedicated risk relationship data plane:

   ```bash
   set -a; . ./.env; set +a
   docker compose --env-file .env --profile authz up -d risk-spicedb
   cd ../auth-platform
   SPICEDB_HTTP=http://localhost:8545 SPICEDB_KEY="$RISK_SPICEDB_KEY" APPLY=1 \
     ./deploy/risk-authz-fixture.sh
   ./mvnw -pl auth-platform-server -am package -DskipTests
   SERVER_PORT=8212 SPICEDB_HTTP=http://localhost:8545 \
   SPICEDB_KEY="$RISK_SPICEDB_KEY" AUTHZ_SERVER_SECURITY_ENABLED=true \
   AUTHZ_SERVER_TOKEN="$AUTHZ_CLIENT_TOKEN" \
     java -jar auth-platform-server/target/auth-platform-server-0.1.0-SNAPSHOT.jar
   ```

4. In another terminal, build and start the risk applications:

   ```bash
   cd ../risk-platform
   docker compose --env-file .env --profile apps up -d --build
   ```

The console image compiles `VITE_CASDOOR_*` into its JavaScript bundle. Rebuild it after changing
issuer, public client ID or organization. `CASDOOR_PUBLIC_URL` is also injected at runtime so its
CSP permits OIDC discovery and token calls.

| Service | Default URL |
| --- | --- |
| Risk console | http://localhost:5173 |
| Fraud gateway | http://localhost:8082 |
| Management API | http://localhost:8083 |
| Decision log | http://localhost:8084 |
| Shared auth-platform Casdoor | http://localhost:8000 |
| Risk auth-platform server | http://localhost:8212 |
| Risk-only SpiceDB HTTP | http://localhost:8545 |
| Nacos | http://localhost:8848/nacos |
| Kibana | http://localhost:5601 |

The browser redirect URI is exactly `http://localhost:5173/auth/callback` (or the configured
Compose host port). Nginx proxies only `/api` and `/actuator`; OAuth code exchange is performed by
the SPA with PKCE.

To rerun the credentialed browser acceptance suite, create a fresh open case first and keep all
credentials in the process environment:

```bash
cd risk-console
E2E_BASE_URL=http://localhost:5173 \
E2E_USERNAME='<risk user>' E2E_PASSWORD='<local password>' \
E2E_CASE_ID='<fresh open case UUID>' \
  npm run e2e:secure -- --project=chromium
```

The suite asserts PKCE code exchange without a browser secret, authenticated API calls, dashboard
access, rule-draft creation, case claim/resolve, session restoration and SSO logout.

## Offline and observability profiles

Run `./scripts/setup-hive.sh` after MySQL is healthy. It starts the `hive` profile and creates only
opt-in local fixtures. Production facts are loaded from Decision/Outbox and case-label tables:

```bash
set -a; . ./.env; set +a
./mvnw -q -DskipTests install
./mvnw -q -pl profiling-offline exec:exec \
  -DofflineMainClass=com.lrj.risk.profiling.offline.RiskFactIngestionJob
./mvnw -q -pl profiling-offline exec:exec
```

`FACT_WATERMARK`, `PROFILE_WATERMARK` and optional `PROFILE_AS_OF` define replay boundaries.
`PROFILE_SERVING_ENABLED=true` also projects affected profiles to Redis and Elasticsearch.

## Configuration checks and cleanup

```bash
docker compose --env-file .env.example --profile full --profile apps --profile hive config --quiet
! docker compose --env-file .env.example --profile apps config --services | grep -qx casdoor
helm lint deploy/helm/risk-platform
helm template risk-local deploy/helm/risk-platform >/tmp/risk-platform.yaml
```

The Helm chart references an existing `risk-platform-secrets` Secret and deploys no identity
provider. `docker compose down` stops containers without deleting data. Volume deletion is not a
normal cleanup step and is intentionally omitted.
