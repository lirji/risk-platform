package com.lrj.risk.fraud.gateway.decision.adapter.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

/** At-least-once outbox relay. Consumers deduplicate by the eventId in each envelope. */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcOutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final MeterRegistry metrics;

    public OutboxRelay(JdbcOutboxRepository repository, KafkaTemplate<String, String> kafka, Clock clock,
                       @Value("${risk.outbox.batch-size:100}") int batchSize,
                       @Value("${risk.outbox.max-attempts:8}") int maxAttempts,
                       MeterRegistry metrics) {
        this.repository = repository;
        this.kafka = kafka;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${risk.outbox.relay-delay-ms:500}")
    public void relay() {
        for (OutboxRecord event : repository.pending(clock.instant(), batchSize)) {
            try {
                kafka.send(event.topic(), event.messageKey(), event.payload()).get(3, TimeUnit.SECONDS);
                repository.published(event.id(), clock.instant());
                metrics.counter("risk.outbox.published.total", "topic", event.topic()).increment();
            } catch (Exception failure) {
                int attempts = event.attempts() + 1;
                long backoffSeconds = Math.min(300, 1L << Math.min(attempts, 8));
                Instant retryAt = clock.instant().plus(Duration.ofSeconds(backoffSeconds));
                repository.failed(event.id(), attempts, maxAttempts, retryAt, failure.toString());
                metrics.counter(attempts >= maxAttempts ? "risk.outbox.dead.total" : "risk.outbox.retry.total",
                        "topic", event.topic()).increment();
                log.warn("outbox publish failed eventId={} attempts={} cause={}",
                        event.eventId(), attempts, failure.toString());
            }
        }
    }
}
