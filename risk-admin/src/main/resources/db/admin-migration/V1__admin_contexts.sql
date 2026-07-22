CREATE TABLE rule_release (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_id VARCHAR(64) NOT NULL,
    rule_code VARCHAR(128) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    drl LONGTEXT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    author_id VARCHAR(128) NOT NULL,
    reviewer_id VARCHAR(128),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_rule_release_id UNIQUE (release_id),
    CONSTRAINT uk_rule_release_version UNIQUE (rule_code, version_no)
);

CREATE TABLE source_rule_binding (
    source_id VARCHAR(64) PRIMARY KEY,
    active_release_id VARCHAR(64) NOT NULL,
    previous_release_id VARCHAR(64),
    rule_sets_json LONGTEXT NOT NULL,
    rollout_percentage INT NOT NULL DEFAULT 100,
    shadow_release_id VARCHAR(64),
    fail_safe_action VARCHAR(16) NOT NULL DEFAULT 'CHALLENGE',
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE tag_definition (
    tag_code VARCHAR(128) PRIMARY KEY,
    tag_name VARCHAR(255) NOT NULL,
    value_type VARCHAR(32) NOT NULL,
    definition_text VARCHAR(2000) NOT NULL,
    freshness_seconds BIGINT NOT NULL,
    version_no INT NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE model_version (
    model_id VARCHAR(64) PRIMARY KEY,
    model_code VARCHAR(128) NOT NULL,
    version_no INT NOT NULL,
    artifact_uri VARCHAR(1000) NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    metrics_json LONGTEXT NOT NULL,
    training_data_version VARCHAR(128) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    reviewed_by VARCHAR(128),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_model_version UNIQUE (model_code, version_no)
);

CREATE TABLE rating_job (
    job_id VARCHAR(64) PRIMARY KEY,
    model_code VARCHAR(128) NOT NULL,
    source_index VARCHAR(255) NOT NULL,
    target_index VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMP(6),
    last_error VARCHAR(1000),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_rating_job_claim ON rating_job(status, lease_until, created_at);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    details_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_admin_audit_created ON audit_log(created_at);
