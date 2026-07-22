CREATE TABLE risk_decision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    txn_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    event_time TIMESTAMP(6) NOT NULL,
    account_token VARCHAR(128) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    action VARCHAR(16) NOT NULL,
    fraud_score DECIMAL(8, 7) NOT NULL,
    hit_rules_json LONGTEXT NOT NULL,
    feature_snapshot_json LONGTEXT NOT NULL,
    rule_version VARCHAR(128) NOT NULL,
    model_version VARCHAR(128) NOT NULL,
    cost_ms BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_risk_decision_id UNIQUE (decision_id),
    CONSTRAINT uk_risk_decision_txn UNIQUE (source_id, txn_id)
);

CREATE INDEX idx_risk_decision_created ON risk_decision(created_at);
CREATE INDEX idx_risk_decision_level ON risk_decision(risk_level, created_at);

CREATE TABLE outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_relay ON outbox_event(status, next_attempt_at, id);

CREATE TABLE inbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(128) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_inbox_consumer_event UNIQUE (consumer_name, event_id)
);

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    details_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_id, created_at);
