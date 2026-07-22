package com.lrj.risk.admin.shared;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditPort audit;
    private final Clock clock;

    public AuditService(AuditPort audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    public void record(String actor, String action, String resourceType, String resourceId, String details) {
        audit.append(actor, action, resourceType, resourceId, UUID.randomUUID().toString(),
                details == null ? "{}" : details, clock.instant());
    }
}
