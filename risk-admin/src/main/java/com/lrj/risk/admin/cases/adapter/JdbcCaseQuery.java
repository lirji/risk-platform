package com.lrj.risk.admin.cases.adapter;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.cases.application.CaseQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCaseQuery implements CaseQuery {
    private final JdbcTemplate jdbc;

    public JdbcCaseQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> findByStatus(String status, int limit) {
        if (status == null || status.isBlank()) {
            return jdbc.queryForList("SELECT * FROM risk_case ORDER BY created_at DESC LIMIT ?", limit);
        }
        return jdbc.queryForList(
                "SELECT * FROM risk_case WHERE status = ? ORDER BY created_at DESC LIMIT ?", status, limit);
    }
}
