CREATE TABLE model_monitor_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_id VARCHAR(64) NOT NULL,
    window_start TIMESTAMP(6) NOT NULL,
    window_end TIMESTAMP(6) NOT NULL,
    sample_count BIGINT NOT NULL,
    score_histogram_json LONGTEXT NOT NULL,
    psi DOUBLE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_model_monitor_window ON model_monitor_snapshot(model_id, window_end);
