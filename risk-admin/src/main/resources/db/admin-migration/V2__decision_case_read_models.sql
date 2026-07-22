CREATE TABLE IF NOT EXISTS risk_decision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL UNIQUE,
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
    CONSTRAINT uk_admin_decision_txn UNIQUE (source_id, txn_id)
);

CREATE TABLE IF NOT EXISTS risk_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL UNIQUE,
    decision_id VARCHAR(64) NOT NULL UNIQUE,
    source_id VARCHAR(64) NOT NULL,
    txn_id VARCHAR(128) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    assignee VARCHAR(128),
    resolution VARCHAR(32),
    resolution_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS case_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS case_label_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL UNIQUE,
    decision_id VARCHAR(64) NOT NULL,
    label VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6)
);
