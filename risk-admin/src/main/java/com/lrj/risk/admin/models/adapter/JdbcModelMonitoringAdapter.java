package com.lrj.risk.admin.models.adapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.models.application.ModelMonitoringPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcModelMonitoringAdapter implements ModelMonitoringPort {
    private final JdbcTemplate jdbc;

    public JdbcModelMonitoringAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> latest(String modelId, int limit) {
        return jdbc.queryForList(
                "SELECT * FROM model_monitor_snapshot WHERE model_id=? ORDER BY window_end DESC LIMIT ?",
                modelId, limit);
    }

    @Override
    public void save(String modelId, Instant windowStart, Instant windowEnd, long sampleCount,
                     String histogramJson, double psi, Instant createdAt) {
        jdbc.update("""
                INSERT INTO model_monitor_snapshot(model_id, window_start, window_end, sample_count,
                    score_histogram_json, psi, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, modelId, Timestamp.from(windowStart), Timestamp.from(windowEnd), sampleCount,
                histogramJson, psi, Timestamp.from(createdAt));
    }
}
