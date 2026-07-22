package com.lrj.risk.fraud.gateway.decision.domain;

import java.time.Instant;

public record OutboxMessage(
        String eventId,
        String aggregateType,
        String aggregateId,
        String topic,
        String messageKey,
        String payload,
        Instant createdAt) {
}
