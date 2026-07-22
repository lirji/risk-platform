package com.lrj.risk.admin.operations.adapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.lrj.risk.admin.operations.application.DeadEventPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeadEventAdapter implements DeadEventPort {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    public JdbcDeadEventAdapter(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Override
    public List<Map<String, Object>> findDead() {
        return jdbc.queryForList("""
                SELECT * FROM (
                    SELECT event_id AS event_id, 'OUTBOX' AS event_kind, topic AS topic,
                           attempts AS attempts, last_error AS last_error,
                           created_at AS created_at
                      FROM outbox_event WHERE status = 'DEAD'
                    UNION ALL
                    SELECT event_id AS event_id, 'KAFKA_DLT' AS event_kind, original_topic AS topic,
                           attempts AS attempts, last_error AS last_error,
                           created_at AS created_at
                      FROM dead_letter_event WHERE status = 'DEAD'
                ) dead_events ORDER BY created_at DESC
                """);
    }

    @Override
    public boolean requestReplay(String eventId, Instant requestedAt) {
        if (jdbc.update("""
                UPDATE outbox_event SET status = 'PENDING', attempts = 0, next_attempt_at = ?, last_error = NULL
                 WHERE event_id = ? AND status = 'DEAD'
                """, Timestamp.from(requestedAt), eventId) == 1) {
            return true;
        }

        List<Map<String, Object>> matches = jdbc.queryForList("""
                SELECT original_topic, message_key, payload FROM dead_letter_event
                 WHERE event_id = ? AND status = 'DEAD'
                """, eventId);
        if (matches.isEmpty()) {
            return false;
        }
        Map<String, Object> event = matches.getFirst();
        String topic = String.valueOf(event.get("original_topic"));
        Object rawKey = event.get("message_key");
        String key = rawKey == null ? null : String.valueOf(rawKey);
        String payload = String.valueOf(event.get("payload"));
        try {
            kafka.send(topic, key, payload).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka DLT replay interrupted", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("Kafka DLT replay failed", failure);
        }
        return jdbc.update("""
                UPDATE dead_letter_event
                   SET status = 'REPLAYED', attempts = attempts + 1, last_error = NULL, replayed_at = ?
                 WHERE event_id = ? AND status = 'DEAD'
                """, Timestamp.from(requestedAt), eventId) == 1;
    }

    @KafkaListener(topics = {"transaction.v1.DLT", "decision.v1.DLT"},
            groupId = "risk-admin-dlt-catalog-v1",
            autoStartup = "${risk.operations.dlt-listener-enabled:false}")
    public void catalog(ConsumerRecord<String, String> record) {
        String eventId = "dlt-" + record.topic() + "-" + record.partition() + "-" + record.offset();
        String originalTopic = record.topic().endsWith(".DLT")
                ? record.topic().substring(0, record.topic().length() - 4)
                : record.topic();
        Instant now = Instant.now();
        int updated = jdbc.update("""
                UPDATE dead_letter_event SET message_key=?, payload=?, last_error=?, status='DEAD'
                 WHERE event_id=?
                """, record.key(), record.value(), "consumer retries exhausted", eventId);
        if (updated == 1) {
            return;
        }
        try {
            jdbc.update("""
                    INSERT INTO dead_letter_event(event_id, dlt_topic, original_topic, partition_no,
                        source_offset, message_key, payload, status, attempts, last_error, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'DEAD', 0, ?, ?)
                    """, eventId, record.topic(), originalTopic, record.partition(), record.offset(),
                    record.key(), record.value(), "consumer retries exhausted", Timestamp.from(now));
        } catch (DuplicateKeyException concurrentInsert) {
            // The DLT record is already catalogued by a concurrent/rebalanced consumer.
        }
    }
}
