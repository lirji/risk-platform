package com.lrj.risk.admin.audit.adapter;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.audit.application.AuditQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditQuery implements AuditQuery {
    private final JdbcTemplate jdbc;

    public JdbcAuditQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> latest(int limit) {
        return jdbc.queryForList("SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ?", limit);
    }
}
