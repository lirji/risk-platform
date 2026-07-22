package com.lrj.risk.admin.operations.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.shared.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {
    private final DeadEventPort deadEvents;
    private final AuditService audit;
    private final Clock clock;

    public OperationsService(DeadEventPort deadEvents, AuditService audit, Clock clock) {
        this.deadEvents = deadEvents;
        this.audit = audit;
        this.clock = clock;
    }

    public List<Map<String, Object>> deadEvents() {
        return deadEvents.findDead();
    }

    @Transactional
    public void replay(String eventId, String actor) {
        if (!deadEvents.requestReplay(eventId, clock.instant())) {
            throw new IllegalStateException("dead event not found");
        }
        audit.record(actor, "DEAD_EVENT_REPLAY_REQUESTED", "DeadEvent", eventId, "{}");
    }
}
