package com.lrj.risk.admin.ratings.adapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.ratings.application.RatingJobPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRatingJobAdapter implements RatingJobPort {
    private final JdbcTemplate jdbc;

    public JdbcRatingJobAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> findAll() {
        return jdbc.queryForList("SELECT * FROM rating_job ORDER BY created_at DESC");
    }

    @Override
    public void create(String jobId, String modelCode, String sourceIndex, String targetIndex,
                       String createdBy, Instant createdAt) {
        Timestamp now = Timestamp.from(createdAt);
        jdbc.update("""
                INSERT INTO rating_job(job_id, model_code, source_index, target_index, status,
                                       attempts, max_attempts, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, 3, ?, ?, ?)
                """, jobId, modelCode, sourceIndex, targetIndex, createdBy, now, now);
    }

    @Override
    public boolean retry(String jobId) {
        return jdbc.update("""
                UPDATE rating_job SET status='PENDING', lease_owner=NULL, lease_until=NULL,
                                      last_error=NULL, updated_at=CURRENT_TIMESTAMP
                 WHERE job_id=? AND status='FAILED' AND attempts < max_attempts
                """, jobId) == 1;
    }
}
