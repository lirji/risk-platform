CREATE DATABASE IF NOT EXISTS casdoor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS metastore_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS risk_platform.business_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    txn_id VARCHAR(128) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    account_token VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    event_time TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_business_transaction UNIQUE (source_id, txn_id)
);
