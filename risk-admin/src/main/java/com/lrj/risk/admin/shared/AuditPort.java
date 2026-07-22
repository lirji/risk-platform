package com.lrj.risk.admin.shared;

import java.time.Instant;

public interface AuditPort {
    void append(String actor, String action, String resourceType, String resourceId,
                String correlationId, String details, Instant createdAt);
}
