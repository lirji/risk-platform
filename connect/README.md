# CDC adapter

`debezium-mysql-source.json` captures the allow-listed business transaction table and publishes `business-transaction.cdc.v1`. Credentials are resolved by Kafka Connect's `EnvVarConfigProvider`; no password is stored in the connector file.

After the `full` Compose profile is healthy, register it with:

```bash
curl -fsS -X POST http://localhost:8088/connectors \
  -H 'Content-Type: application/json' \
  --data @connect/debezium-mysql-source.json
```

The CDC event is an ingestion adapter, not the authoritative risk decision event. Production should narrow the database account to `SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT` and keep the table allow-list explicit.
