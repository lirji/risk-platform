package com.lrj.risk.admin.decisions.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.decisions.application.DecisionQuery;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDecisionQuery implements DecisionQuery {
    private final JdbcTemplate jdbc;

    public JdbcDecisionQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page<DecisionView> search(String riskLevel, String transactionId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = safePage * safeSize;
        var filters = new java.util.ArrayList<String>();
        var parameters = new java.util.ArrayList<Object>();
        if (riskLevel != null && !riskLevel.isBlank()) {
            filters.add("risk_level = ?");
            parameters.add(riskLevel);
        }
        if (transactionId != null && !transactionId.isBlank()) {
            filters.add("txn_id = ?");
            parameters.add(transactionId.trim());
        }
        String where = filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM risk_decision" + where,
                Long.class, parameters.toArray());
        var pageParameters = new java.util.ArrayList<>(parameters);
        pageParameters.add(safeSize);
        pageParameters.add(offset);
        List<DecisionView> content = jdbc.query("""
                SELECT decision_id, source_id, txn_id, event_time, risk_level, action, fraud_score,
                       hit_rules_json, rule_version, model_version, cost_ms, created_at
                  FROM risk_decision
                """ + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?", this::map,
                pageParameters.toArray());
        return new Page<>(content, total == null ? 0 : total, safePage, safeSize);
    }

    @Override
    public Map<String, Object> detail(String decisionId) {
        try {
            return jdbc.queryForMap("""
                    SELECT decision_id, source_id, txn_id, correlation_id, event_time, account_token,
                           risk_level, action, fraud_score, hit_rules_json, feature_snapshot_json,
                           rule_version, model_version, cost_ms, created_at
                      FROM risk_decision WHERE decision_id = ?
                    """, decisionId);
        } catch (EmptyResultDataAccessException missing) {
            throw new IllegalArgumentException("decision not found: " + decisionId, missing);
        }
    }

    private DecisionView map(ResultSet rs, int row) throws SQLException {
        return new DecisionView(rs.getString("decision_id"), rs.getString("source_id"),
                rs.getString("txn_id"), rs.getTimestamp("event_time").toInstant(),
                rs.getString("risk_level"), rs.getString("action"), rs.getDouble("fraud_score"),
                rs.getString("hit_rules_json"), rs.getString("rule_version"),
                rs.getString("model_version"), rs.getLong("cost_ms"),
                rs.getTimestamp("created_at").toInstant());
    }
}
