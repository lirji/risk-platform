package com.lrj.risk.admin.shared.adapter;

import java.sql.Timestamp;
import java.time.Instant;

import com.lrj.risk.admin.shared.AuditPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditAdapter implements AuditPort {
    private final JdbcTemplate jdbc;

    public JdbcAuditAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String actor, String action, String resourceType, String resourceId,
                       String correlationId, String details, Instant createdAt) {
        jdbc.update("""
                INSERT INTO audit_log(actor_id, action, resource_type, resource_id,
                                      correlation_id, details_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, actor, action, resourceType, resourceId, correlationId, details,
                Timestamp.from(createdAt));
    }
}
