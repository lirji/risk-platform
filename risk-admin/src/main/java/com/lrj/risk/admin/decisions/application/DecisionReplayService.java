package com.lrj.risk.admin.decisions.application;

import java.time.Clock;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lrj.risk.admin.shared.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionReplayService {

    private final DecisionReplayPort replayEvents;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public DecisionReplayService(DecisionReplayPort replayEvents, ObjectMapper mapper,
                                 AuditService audit, Clock clock) {
        this.replayEvents = replayEvents; this.mapper = mapper; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public String replay(String decisionId, String actor, String reason) {
        DecisionReplayPort.ReplaySource source = replayEvents.findSource(decisionId)
                .orElseThrow(() -> new IllegalArgumentException("decision event not found"));
        try {
            String eventId = UUID.randomUUID().toString();
            ObjectNode payload = (ObjectNode) mapper.readTree(source.payload());
            ObjectNode metadata = (ObjectNode) payload.path("metadata");
            metadata.put("eventId", eventId);
            metadata.put("occurredAt", clock.instant().toString());
            var now = clock.instant();
            replayEvents.enqueue(eventId, decisionId, source.messageKey(),
                    mapper.writeValueAsString(payload), now);
            audit.record(actor, "DECISION_REPLAY_REQUESTED", "Decision", decisionId,
                    mapper.writeValueAsString(java.util.Map.of("reason", reason, "eventId", eventId)));
            return eventId;
        } catch (Exception exception) {
            throw new IllegalStateException("decision replay creation failed", exception);
        }
    }
}
