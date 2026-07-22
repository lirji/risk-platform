package com.lrj.risk.admin.cases.application;

import java.time.Clock;

import com.lrj.risk.admin.shared.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseWorkflowService {

    private final CaseWorkflowPort cases;
    private final CaseAuthorization authorization;
    private final AuditService audit;
    private final Clock clock;

    public CaseWorkflowService(CaseWorkflowPort cases, CaseAuthorization authorization,
                               AuditService audit, Clock clock) {
        this.cases = cases;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void claim(String tenant, String caseId, String actor) {
        if (!cases.claim(caseId, actor, clock.instant())) {
            throw new IllegalStateException("case is not open or does not exist");
        }
        authorization.assign(tenant, caseId, actor);
        audit.record(actor, "CASE_CLAIMED", "RiskCase", caseId, "{}");
    }

    @Transactional
    public void comment(String tenant, String caseId, String actor, String content) {
        authorization.requireWork(tenant, caseId, actor);
        if (!cases.exists(caseId)) throw new IllegalArgumentException("case not found");
        cases.addComment(caseId, actor, content, clock.instant());
        audit.record(actor, "CASE_COMMENTED", "RiskCase", caseId, "{}");
    }

    @Transactional
    public void resolve(String tenant, String caseId, String actor, String label, String reason) {
        authorization.requireWork(tenant, caseId, actor);
        if (!"FRAUD".equals(label) && !"NORMAL".equals(label)) {
            throw new IllegalArgumentException("label must be FRAUD or NORMAL");
        }
        var now = clock.instant();
        String decisionId = cases.resolve(caseId, actor, label, reason, now)
                .orElseThrow(() -> new IllegalStateException("case is not claimed by current actor"));
        cases.addLabelFeedback(caseId, decisionId, label, actor, now);
        audit.record(actor, "CASE_RESOLVED", "RiskCase", caseId,
                "{\"label\":\"" + label + "\"}");
    }
}
