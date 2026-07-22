# Operations runbook

## Decision latency

1. Confirm P99/P999 in Grafana and split by `risk.decision.stage.duration` (`feature`, `engine`, `persistence`).
2. Check MySQL pool saturation, Redis latency, model reload errors, JVM GC, and Kafka/outbox backlog.
3. Preserve fail-safe behavior. Do not make unavailable features default to ALLOW to recover latency.
4. Roll back the most recent rule/model deployment if the regression correlates with a version change.

## Dead outbox or DLT messages

1. Diagnose and repair the consumer/schema/infrastructure cause first.
2. Open Operations as a user with `ops.read`; inspect event ID, topic, attempts, and last error.
3. A user with `ops.replay` may replay one event. Outbox replay resets its delivery state; Kafka DLT replay republishes the catalogued payload to its recorded original topic. Both paths create an immutable audit entry and retain the dead-event catalog record.
4. Verify downstream inbox idempotency and lag after replay. Avoid mass replay without a reviewed change ticket.

## Rule rollback

Use the Rules page or `POST /api/v1/rules/releases/{activeReleaseId}/rollback` with `{"sourceId":"..."}`. The service validates that the supplied release is active, atomically reloads the previous compiled release, swaps the binding, and writes an audit record. A release with no previous binding is not rollbackable.

Canary selection is deterministic by `sourceId + txnId`; repeated evaluations stay in the same cohort. Shadow releases never change the returned action. Compare the active/candidate metrics before promotion, and roll back the active release if latency or action distribution regresses.

## Model rollback

Use the Models page or `POST /api/v1/models/{retiredModelId}/rollback`. Only a previously reviewed and activated model is eligible. Artifact URI prefix and checksum are verified by the gateway before the PMML scorer swaps its atomic reference.

Changing a canary percentage keeps the current stable model as fallback; promoting to 100% atomically makes the candidate stable. Watch score distribution and PSI panels before and after every change.

## Offline facts and profiles

1. Record the last successful `FACT_WATERMARK` and `PROFILE_WATERMARK` in the scheduler metadata.
2. Run `RiskFactIngestionJob`; verify composite-key counts in `dwd_transaction_fact`, `dwd_decision_feature`, and `fact_case_label`.
3. Run `OfflineProfileJob` with a fixed `PROFILE_AS_OF`; compare affected-customer counts and `dws_customer_profile` row counts.
4. Advance watermarks only after both jobs and optional Redis/ES projections succeed. Reusing the old boundary is safe because fact merges use `source_id + txn_id` and profile updates replace affected customers.

## Kafka consumer lag

Check the Kafka exporter panel by group/topic. Scale consumers only up to the partition count. For persistent lag, inspect poison records and DLT rate, then confirm Redis/ES destination latency. Never reset offsets before exporting the old offsets and obtaining change approval.

## Nacos unavailable

Both applications use `optional:nacos:` imports. They continue with conservative local defaults. Do not put credentials in Nacos. Restore Nacos, verify `RISK_PLATFORM` group values, and observe refresh logs before changing runtime thresholds.

## Backup and recovery

- MySQL: backup the risk application schema with consistent snapshots; test restore quarterly. Shared Casdoor backup belongs to the auth-platform recovery plan, not this stack.
- Elasticsearch: snapshot decision/transaction indexes and Kibana saved objects; ILM deletes decision indexes after the configured retention.
- MinIO/Hive: version artifacts and Parquet data; Hive Metastore backup alone is insufficient.
- Kafka: topic retention is not a database backup. The decision/outbox tables remain the authoritative replay source.
