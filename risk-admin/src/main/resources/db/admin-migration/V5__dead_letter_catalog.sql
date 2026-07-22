CREATE TABLE dead_letter_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL,
    dlt_topic VARCHAR(128) NOT NULL,
    original_topic VARCHAR(128) NOT NULL,
    partition_no INT NOT NULL,
    source_offset BIGINT NOT NULL,
    message_key VARCHAR(256),
    payload LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    replayed_at TIMESTAMP(6),
    CONSTRAINT uk_dead_letter_event_id UNIQUE (event_id),
    CONSTRAINT uk_dead_letter_position UNIQUE (dlt_topic, partition_no, source_offset)
);

CREATE INDEX idx_dead_letter_status_created ON dead_letter_event(status, created_at);
