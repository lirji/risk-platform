package com.lrj.risk.admin.cases.adapter.authz;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import com.lrj.authz.protocol.AuthzEngine;
import com.lrj.authz.protocol.Consistency;
import com.lrj.authz.protocol.RelationshipUpdate;
import com.lrj.authz.protocol.ResourceRef;
import com.lrj.authz.protocol.SubjectRef;
import com.lrj.risk.admin.cases.application.CaseAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** auth-platform SDK 案件 assignee 关系适配器。 */
final class RemoteCaseAuthorization implements CaseAuthorization {
    enum Mode { DISABLED, SHADOW, ENFORCE }

    private static final Logger log = LoggerFactory.getLogger(RemoteCaseAuthorization.class);
    private final Mode mode;
    private final ObjectProvider<AuthzEngine> engines;
    private final AtomicReference<String> lastWriteToken = new AtomicReference<>();

    RemoteCaseAuthorization(String mode, ObjectProvider<AuthzEngine> engines) {
        this.mode = parseMode(mode);
        this.engines = engines;
    }

    @Override
    public void assign(String tenant, String caseId, String actor) {
        if (mode == Mode.DISABLED) return;
        RelationshipUpdate assignment = RelationshipUpdate.touch(
                resource(tenant, caseId), "assignee", SubjectRef.user(required(actor, "actor")));
        try {
            String token = engine().writeRelationships(List.of(assignment)).token();
            lastWriteToken.set(token);
            compensateAssignmentOnRollback(assignment);
        } catch (RuntimeException exception) {
            if (mode == Mode.ENFORCE) throw unavailable("案件授权关系写入失败", exception);
            log.warn("[authz-shadow] 案件 {} assignee 写入失败: {}", caseId, exception.getMessage());
        }
    }

    @Override
    public void requireWork(String tenant, String caseId, String actor) {
        if (mode == Mode.DISABLED) return;
        boolean allowed;
        try {
            allowed = engine().check(SubjectRef.user(required(actor, "actor")), "work",
                    resource(tenant, caseId), consistency());
        } catch (RuntimeException exception) {
            if (mode == Mode.ENFORCE) throw unavailable("案件判权服务不可用", exception);
            log.warn("[authz-shadow] 案件 {} 判权失败后放行: {}", caseId, exception.getMessage());
            return;
        }
        if (!allowed) {
            if (mode == Mode.ENFORCE) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有案件 assignee 可以执行该操作");
            }
            log.info("[authz-shadow] would-deny actor={} risk_case:{} work", actor, objectId(tenant, caseId));
        }
    }

    static Mode parseMode(String value) {
        String normalized = value == null ? "disabled" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "disabled" -> Mode.DISABLED;
            case "shadow" -> Mode.SHADOW;
            case "enforce" -> Mode.ENFORCE;
            default -> throw new IllegalArgumentException("risk.authz.mode 仅支持 disabled/shadow/enforce: " + value);
        };
    }

    private AuthzEngine engine() {
        AuthzEngine engine = engines.getIfAvailable();
        if (engine == null) throw new IllegalStateException("auth-platform SDK 未装配");
        return engine;
    }

    private ResourceRef resource(String tenant, String caseId) {
        return ResourceRef.of("risk_case", objectId(tenant, caseId));
    }

    private String objectId(String tenant, String caseId) {
        return required(tenant, "tenant") + "_" + required(caseId, "caseId");
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private Consistency consistency() {
        String token = lastWriteToken.get();
        return token == null ? Consistency.minimizeLatency() : Consistency.atLeastAsFresh(token);
    }

    private void compensateAssignmentOnRollback(RelationshipUpdate assignment) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;
                try {
                    String token = engine().writeRelationships(List.of(RelationshipUpdate.delete(
                            assignment.resource(), assignment.relation(), assignment.subject()))).token();
                    lastWriteToken.set(token);
                } catch (RuntimeException exception) {
                    log.error("案件事务回滚后的 assignee 关系补偿失败 resource={} actor={}: {}",
                            assignment.resource(), assignment.subject(), exception.getMessage());
                }
            }
        });
    }

    private ResponseStatusException unavailable(String message, RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
