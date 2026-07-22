package com.lrj.risk.fraud.gateway.decision.adapter.outbox;

record OutboxRecord(long id, String eventId, String topic, String messageKey,
                    String payload, int attempts) {
}
