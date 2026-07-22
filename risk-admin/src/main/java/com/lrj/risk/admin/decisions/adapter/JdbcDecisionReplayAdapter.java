package com.lrj.risk.admin.decisions.adapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import com.lrj.risk.admin.decisions.application.DecisionReplayPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDecisionReplayAdapter implements DecisionReplayPort {
    private final JdbcTemplate jdbc;

    public JdbcDecisionReplayAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReplaySource> findSource(String decisionId) {
        return jdbc.query("""
                SELECT message_key, payload FROM outbox_event
                 WHERE aggregate_id = ? AND topic = 'decision.v1'
                 ORDER BY id DESC LIMIT 1
                """, (rs, row) -> new ReplaySource(rs.getString("message_key"), rs.getString("payload")),
                decisionId).stream().findFirst();
    }

    @Override
    public void enqueue(String eventId, String decisionId, String messageKey, String payload, Instant createdAt) {
        Timestamp now = Timestamp.from(createdAt);
        jdbc.update("""
                INSERT INTO outbox_event(event_id, aggregate_type, aggregate_id, topic, message_key,
                    payload, status, attempts, next_attempt_at, created_at)
                VALUES (?, 'DecisionReplay', ?, 'decision.v1', ?, ?, 'PENDING', 0, ?, ?)
                """, eventId, decisionId, messageKey, payload, now, now);
    }
}
