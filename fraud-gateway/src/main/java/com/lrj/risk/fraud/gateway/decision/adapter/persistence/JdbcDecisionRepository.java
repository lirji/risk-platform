package com.lrj.risk.fraud.gateway.decision.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.kernel.TransactionKey;
import com.lrj.risk.fraud.gateway.decision.application.port.out.DecisionRepository;
import com.lrj.risk.fraud.gateway.decision.domain.DecisionRecord;
import com.lrj.risk.fraud.gateway.decision.domain.OutboxMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDecisionRepository implements DecisionRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcDecisionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<DecisionRecord> findByTransactionKey(TransactionKey key) {
        List<DecisionRecord> matches = jdbc.query("""
                SELECT decision_id, source_id, txn_id, correlation_id, event_time, account_token,
                       risk_level, action, fraud_score, hit_rules_json, feature_snapshot_json,
                       rule_version, model_version, cost_ms, created_at
                  FROM risk_decision
                 WHERE source_id = ? AND txn_id = ?
                """, this::mapDecision, key.sourceId(), key.txnId());
        return matches.stream().findFirst();
    }

    @Override
    public void save(DecisionRecord decision, List<OutboxMessage> events) {
        jdbc.update("""
                INSERT INTO risk_decision (
                    decision_id, source_id, txn_id, correlation_id, event_time, account_token,
                    risk_level, action, fraud_score, hit_rules_json, feature_snapshot_json,
                    rule_version, model_version, cost_ms, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, decision.decisionId(), decision.sourceId(), decision.txnId(),
                decision.correlationId(), Timestamp.from(decision.eventTime()), decision.accountToken(),
                decision.riskLevel(), decision.action(), decision.fraudScore(), json(decision.hitRules()),
                json(decision.featureSnapshot()), decision.ruleVersion(), decision.modelVersion(),
                decision.costMs(), Timestamp.from(decision.createdAt()));
        for (OutboxMessage event : events) {
            jdbc.update("""
                    INSERT INTO outbox_event (
                        event_id, aggregate_type, aggregate_id, topic, message_key, payload,
                        status, attempts, next_attempt_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                    """, event.eventId(), event.aggregateType(), event.aggregateId(), event.topic(),
                    event.messageKey(), event.payload(), Timestamp.from(event.createdAt()),
                    Timestamp.from(event.createdAt()));
        }
        if ("REVIEW".equals(decision.action()) || "CHALLENGE".equals(decision.action())) {
            jdbc.update("""
                    INSERT INTO risk_case (
                        case_id, decision_id, source_id, txn_id, risk_level, status,
                        version, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'OPEN', 0, ?, ?)
                    """, java.util.UUID.randomUUID().toString(), decision.decisionId(), decision.sourceId(),
                    decision.txnId(), decision.riskLevel(), Timestamp.from(decision.createdAt()),
                    Timestamp.from(decision.createdAt()));
        }
    }

    @Override
    public void updateCost(String decisionId, long costMs) {
        jdbc.update("UPDATE risk_decision SET cost_ms = ? WHERE decision_id = ?", costMs, decisionId);
    }

    private DecisionRecord mapDecision(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            return new DecisionRecord(
                    resultSet.getString("decision_id"), resultSet.getString("source_id"),
                    resultSet.getString("txn_id"), resultSet.getString("correlation_id"),
                    resultSet.getTimestamp("event_time").toInstant(), resultSet.getString("account_token"),
                    resultSet.getString("risk_level"), resultSet.getString("action"),
                    resultSet.getDouble("fraud_score"),
                    objectMapper.readValue(resultSet.getString("hit_rules_json"), STRING_LIST),
                    objectMapper.readValue(resultSet.getString("feature_snapshot_json"), STRING_MAP),
                    resultSet.getString("rule_version"), resultSet.getString("model_version"),
                    resultSet.getLong("cost_ms"), resultSet.getTimestamp("created_at").toInstant());
        } catch (JsonProcessingException exception) {
            throw new SQLException("invalid decision JSON", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("decision serialization failed", exception);
        }
    }
}
