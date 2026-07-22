package com.lrj.risk.admin.cases.application;

import java.time.Instant;
import java.util.Optional;

public interface CaseWorkflowPort {
    boolean claim(String caseId, String actor, Instant updatedAt);

    boolean exists(String caseId);

    void addComment(String caseId, String actor, String content, Instant createdAt);

    Optional<String> resolve(String caseId, String actor, String label, String reason, Instant resolvedAt);

    void addLabelFeedback(String caseId, String decisionId, String label, String actor, Instant createdAt);
}
