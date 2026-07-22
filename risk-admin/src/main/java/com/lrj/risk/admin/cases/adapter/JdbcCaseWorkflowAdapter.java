package com.lrj.risk.admin.cases.adapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import com.lrj.risk.admin.cases.application.CaseWorkflowPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCaseWorkflowAdapter implements CaseWorkflowPort {
    private final JdbcTemplate jdbc;

    public JdbcCaseWorkflowAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String caseId, String actor, Instant updatedAt) {
        return jdbc.update("""
                UPDATE risk_case SET status = 'CLAIMED', assignee = ?, version = version + 1,
                                     updated_at = ?
                 WHERE case_id = ? AND status = 'OPEN'
                """, actor, Timestamp.from(updatedAt), caseId) == 1;
    }

    @Override
    public boolean exists(String caseId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk_case WHERE case_id = ?", Integer.class, caseId);
        return count != null && count > 0;
    }

    @Override
    public void addComment(String caseId, String actor, String content, Instant createdAt) {
        jdbc.update("INSERT INTO case_comment(case_id, actor_id, content, created_at) VALUES (?, ?, ?, ?)",
                caseId, actor, content, Timestamp.from(createdAt));
    }

    @Override
    public Optional<String> resolve(String caseId, String actor, String label, String reason, Instant resolvedAt) {
        var decisions = jdbc.query("""
                SELECT decision_id FROM risk_case
                 WHERE case_id = ? AND status = 'CLAIMED' AND assignee = ?
                """, (rs, row) -> rs.getString("decision_id"), caseId, actor);
        if (decisions.isEmpty()) return Optional.empty();
        int updated = jdbc.update("""
                UPDATE risk_case SET status = 'RESOLVED', resolution = ?, resolution_reason = ?,
                                     version = version + 1, updated_at = ?
                 WHERE case_id = ? AND status = 'CLAIMED' AND assignee = ?
                """, label, reason, Timestamp.from(resolvedAt), caseId, actor);
        return updated == 1 ? Optional.of(decisions.getFirst()) : Optional.empty();
    }

    @Override
    public void addLabelFeedback(String caseId, String decisionId, String label, String actor, Instant createdAt) {
        jdbc.update("""
                INSERT INTO case_label_feedback(case_id, decision_id, label, actor_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, caseId, decisionId, label, actor, Timestamp.from(createdAt));
    }
}
