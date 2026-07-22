package com.lrj.risk.admin.decisions.application;

import java.time.Instant;
import java.util.Optional;

public interface DecisionReplayPort {
    Optional<ReplaySource> findSource(String decisionId);

    void enqueue(String eventId, String decisionId, String messageKey, String payload, Instant createdAt);

    record ReplaySource(String messageKey, String payload) { }
}
