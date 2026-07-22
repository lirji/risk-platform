CREATE TABLE risk_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL,
    decision_id VARCHAR(64) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    txn_id VARCHAR(128) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    assignee VARCHAR(128),
    resolution VARCHAR(32),
    resolution_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_risk_case_id UNIQUE (case_id),
    CONSTRAINT uk_risk_case_decision UNIQUE (decision_id)
);

CREATE INDEX idx_risk_case_status ON risk_case(status, created_at);

CREATE TABLE case_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE case_label_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL,
    decision_id VARCHAR(64) NOT NULL,
    label VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_case_label_feedback UNIQUE (case_id)
);
