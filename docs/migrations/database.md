# Database migration policy

Gateway migrations use the default Flyway history table. Management bounded contexts use `flyway_admin_history` and `classpath:db/admin-migration`, so both applications can share the local `risk_platform` schema without claiming each other's versions.

- Migrations are forward-only and immutable after release.
- Prefer expand-and-contract: add nullable/backfilled structures, deploy compatible readers/writers, then remove old structures in a later release.
- Never run destructive cleanup automatically during application rollback.
- Decision/outbox unique constraints and status columns are correctness mechanisms, not optional indexes.
- Before production migration, back up MySQL, run Flyway validation against a restored copy, and record row counts/checksums for critical tables.

Local H2 runs in MySQL compatibility mode for fast integration tests; production migration rehearsal must still run against the supported MySQL 8 version.

The current management schema is at admin migration V5. V5 adds the unified Kafka-DLT catalog;
it stores the original topic and replay metadata separately from the Outbox dead-event source. The
operations list API deliberately omits the retained payload. Before enabling the DLT listener,
apply V1–V5, validate the unique event ID constraint, and grant the application account only the
required DML privileges.
