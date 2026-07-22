# DDD context map

## Boundary decisions

The platform separates the synchronous data plane from the management control plane. `fraud-gateway` owns the latency-sensitive Risk Decision application; `risk-admin` is a modular monolith containing management bounded contexts. Spark, Flink, Drools, PMML, Redis, Kafka, and Casdoor are adapters, not domain concepts.

| Bounded context | Aggregate / responsibility | Published interface |
| --- | --- | --- |
| Risk Decision | immutable Decision; `sourceId + txnId` invariant | evaluation API, `transaction.v1`, `decision.v1` |
| Feature & Profile | event-time feature accumulator, feature/tag definitions | FeatureReader port, profile query API |
| Rule Governance | RuleRelease and source binding lifecycle | approved runtime deployment port |
| Model Governance | evaluated ModelVersion, deployment, drift | approved runtime deployment port |
| Case Workflow | claim, comment, disposition, label feedback | case API and authoritative label table |
| Rating | leased RatingTask and score output | rating job API and ES projection |
| Audit & Operations | immutable audit entry, dead event, replay request | audit/operations APIs |
| Identity & Access | principal-to-permission anti-corruption layer | Spring Security authorities mapped from Casdoor |

## Dependency direction

```text
adapter.in  ──> application/use case ──> domain
                         │
                         └──> application ports <── adapter.out
```

The domain packages are framework-free and enforced by ArchUnit. Cross-context changes use versioned events or application facades; controllers do not exchange persistence entities. `platform-contracts` is the Published Language for Kafka and error contracts. `common-feature` is a small Shared Kernel containing feature semantics only.

## Important invariants

- A completed decision is never edited. Replay creates a new event with its own ID and audit entry.
- Decision and outbox records commit in the same transaction. Consumers are at-least-once and deduplicate by `metadata.eventId`.
- Feature unavailability is unknown risk, never low risk; the default fail-safe action is CHALLENGE or stricter.
- Rule/model creators cannot approve their own artifact. Runtime activation occurs only after approval and retains a rollback target.
- Case disposition requires a reason and authoritative `FRAUD`, `NORMAL`, or `UNCERTAIN` label.
- Rating task claim is an atomic lease; partial Elasticsearch bulk failure fails the job rather than silently succeeding.
- Canary routing for rules and models is deterministic by `sourceId + txnId`; shadow evaluation is observational and cannot change the active decision.
- Kafka DLT payload access is confined to the operations adapter. APIs expose metadata only, and authorized replay publishes to the recorded original topic.
- Offline facts are privacy-safe projections keyed by `source_id + txn_id`; account tokenization is performed before Hive persistence.

## Module audit outcome

- `common-feature`: refactored; Spring Redis and event DTOs were removed from the shared domain.
- `fraud-engine`: retained as the decision domain; Drools and PMML are outbound adapters behind ports.
- `fraud-gateway`: refactored into web adapter, application use case, domain records, and persistence/security/outbox adapters.
- `profiling-realtime` and `profiling-realtime-flink`: share pure event-time accumulation semantics; runtime state and Redis are adapters.
- `fraud-model-train` and `rating-engine`: retained as batch applications; configuration stores, Hive, Spark, artifact and ES concerns remain outside domain types.
- `risk-admin`: new modular management control plane. Its contexts are packages, not independent services, avoiding distributed-transaction overhead.
- `profiling-offline`: retained as a batch adapter around immutable facts; deterministic Spark transformations are separated from JDBC/Hive/Redis/ES runtime wiring and covered by a local fixture test.
