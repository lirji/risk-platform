package com.lrj.risk.fraud.gateway.decision.adapter.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboxRepository {

    private final JdbcTemplate jdbc;

    JdbcOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<OutboxRecord> pending(Instant now, int limit) {
        return jdbc.query("""
                SELECT id, event_id, topic, message_key, payload, attempts
                  FROM outbox_event
                 WHERE status = 'PENDING' AND next_attempt_at <= ?
                 ORDER BY id
                 LIMIT ?
                """, (rs, row) -> new OutboxRecord(rs.getLong("id"), rs.getString("event_id"),
                rs.getString("topic"), rs.getString("message_key"), rs.getString("payload"),
                rs.getInt("attempts")), Timestamp.from(now), limit);
    }

    void published(long id, Instant now) {
        jdbc.update("""
                UPDATE outbox_event
                   SET status = 'PUBLISHED', published_at = ?, last_error = NULL
                 WHERE id = ? AND status = 'PENDING'
                """, Timestamp.from(now), id);
    }

    void failed(long id, int attempts, int maxAttempts, Instant nextAttempt, String error) {
        String status = attempts >= maxAttempts ? "DEAD" : "PENDING";
        jdbc.update("""
                UPDATE outbox_event
                   SET status = ?, attempts = ?, next_attempt_at = ?, last_error = ?
                 WHERE id = ? AND status = 'PENDING'
                """, status, attempts, Timestamp.from(nextAttempt), abbreviate(error), id);
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
