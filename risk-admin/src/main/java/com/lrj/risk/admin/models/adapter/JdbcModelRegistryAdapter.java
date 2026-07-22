package com.lrj.risk.admin.models.adapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lrj.risk.admin.models.application.ModelRegistryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcModelRegistryAdapter implements ModelRegistryPort {
    private final JdbcTemplate jdbc;

    public JdbcModelRegistryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(String id, String code, int version, String artifactUri, String checksum,
                       String metricsJson, String dataVersion, String actor, Instant createdAt) {
        Timestamp now = Timestamp.from(createdAt);
        jdbc.update("""
                INSERT INTO model_version(model_id, model_code, version_no, artifact_uri, checksum,
                    status, metrics_json, training_data_version, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'REGISTERED', ?, ?, ?, ?, ?)
                """, id, code, version, artifactUri, checksum, metricsJson, dataVersion, actor, now, now);
    }

    @Override
    public boolean approve(String id, String reviewer, Instant updatedAt) {
        return jdbc.update("""
                UPDATE model_version SET status='APPROVED', reviewed_by=?, updated_at=?
                 WHERE model_id=? AND status='REGISTERED' AND created_by <> ?
                """, reviewer, Timestamp.from(updatedAt), id, reviewer) == 1;
    }

    @Override
    public Optional<Activation> deploymentCandidate(String id) {
        return jdbc.query("""
                SELECT model_code, version_no, artifact_uri, checksum, status FROM model_version
                 WHERE model_id=? AND status IN ('APPROVED', 'CANARY')
                """, (rs, row) -> new Activation(rs.getString("model_code"), rs.getInt("version_no"),
                rs.getString("artifact_uri"), rs.getString("checksum"), rs.getString("status")),
                id).stream().findFirst();
    }

    @Override
    public Optional<Activation> rollbackTarget(String id) {
        return jdbc.query("""
                SELECT model_code, version_no, artifact_uri, checksum, status FROM model_version
                 WHERE model_id=? AND status IN ('RETIRED', 'STABLE') AND reviewed_by IS NOT NULL
                """, (rs, row) -> new Activation(rs.getString("model_code"), rs.getInt("version_no"),
                rs.getString("artifact_uri"), rs.getString("checksum"), rs.getString("status")),
                id).stream().findFirst();
    }

    @Override
    public boolean hasServingDeployment(String modelCode) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM model_version
                 WHERE model_code=? AND status IN ('ACTIVE', 'STABLE')
                """, Integer.class, modelCode);
        return count != null && count > 0;
    }

    @Override
    public void prepareDeployment(String modelCode, String candidateId, int rolloutPercentage, Instant updatedAt) {
        Timestamp now = Timestamp.from(updatedAt);
        if (rolloutPercentage < 100) {
            jdbc.update("""
                    UPDATE model_version SET status='RETIRED', rollout_percentage=0, updated_at=?
                     WHERE model_code=? AND model_id<>? AND status='CANARY'
                    """, now, modelCode, candidateId);
            jdbc.update("""
                    UPDATE model_version SET status='STABLE', rollout_percentage=?, updated_at=?
                     WHERE model_code=? AND status IN ('ACTIVE', 'STABLE')
                    """, 100 - rolloutPercentage, now, modelCode);
        } else {
            jdbc.update("""
                    UPDATE model_version SET status='RETIRED', rollout_percentage=0, updated_at=?
                     WHERE model_code=? AND model_id<>? AND status IN ('ACTIVE', 'STABLE', 'CANARY')
                    """, now, modelCode, candidateId);
        }
    }

    @Override
    public boolean activate(String id, String expectedStatus, String targetStatus,
                            int rolloutPercentage, Instant updatedAt) {
        return jdbc.update("""
                UPDATE model_version SET status=?, rollout_percentage=?, updated_at=?
                 WHERE model_id=? AND status=?
                """, targetStatus, rolloutPercentage, Timestamp.from(updatedAt), id, expectedStatus) == 1;
    }

    @Override
    public List<Map<String, Object>> findAll() {
        return jdbc.queryForList("SELECT * FROM model_version ORDER BY created_at DESC");
    }
}
