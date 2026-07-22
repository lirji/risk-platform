package com.lrj.risk.admin.rules.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.lrj.risk.admin.rules.application.port.RuleReleaseRepository;
import com.lrj.risk.admin.rules.application.port.RuleRuntimePort;
import com.lrj.risk.admin.rules.domain.RuleRelease;
import com.lrj.risk.admin.rules.domain.RuleReleaseStatus;
import com.lrj.risk.admin.shared.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleReleaseService {

    private final RuleReleaseRepository repository;
    private final RuleRuntimePort runtime;
    private final AuditService audit;
    private final Clock clock;

    public RuleReleaseService(RuleReleaseRepository repository, RuleRuntimePort runtime,
                              AuditService audit, Clock clock) {
        this.repository = repository;
        this.runtime = runtime;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public RuleRelease create(String ruleCode, String name, String drl, String actor) {
        var now = clock.instant();
        RuleRelease release = new RuleRelease(UUID.randomUUID().toString(), ruleCode, name,
                repository.nextVersion(ruleCode), drl, sha256(drl), actor, RuleReleaseStatus.DRAFT,
                null, now, now);
        repository.insert(release);
        audit.record(actor, "RULE_RELEASE_CREATED", "RuleRelease", release.releaseId(), "{}");
        return release;
    }

    @Transactional
    public RuleRelease submit(String id, String actor) {
        RuleRelease release = required(id);
        release.submit(clock.instant());
        repository.update(release);
        audit.record(actor, "RULE_RELEASE_SUBMITTED", "RuleRelease", id, "{}");
        return release;
    }

    @Transactional
    public RuleRelease approve(String id, String reviewer) {
        RuleRelease release = required(id);
        release.approve(reviewer, clock.instant());
        repository.update(release);
        audit.record(reviewer, "RULE_RELEASE_APPROVED", "RuleRelease", id, "{}");
        return release;
    }

    @Transactional
    public RuleRelease publish(String id, String sourceId, List<String> groups, int rollout,
                               String shadowReleaseId, String actor) {
        if (rollout < 0 || rollout > 100) throw new IllegalArgumentException("rollout must be 0..100");
        RuleRelease release = required(id);
        if (release.status() != RuleReleaseStatus.APPROVED) {
            throw new IllegalStateException("release must be approved before publication");
        }
        RuleRelease previous = repository.binding(sourceId)
                .flatMap(binding -> repository.find(binding.activeReleaseId())).orElse(null);
        if (rollout < 100 && previous == null) {
            throw new IllegalStateException("partial rollout requires an existing active release");
        }
        RuleRelease shadow = shadowReleaseId == null || shadowReleaseId.isBlank() ? null
                : required(shadowReleaseId);
        if (shadow != null && shadow.status() != RuleReleaseStatus.APPROVED
                && shadow.status() != RuleReleaseStatus.PUBLISHED
                && shadow.status() != RuleReleaseStatus.RETIRED) {
            throw new IllegalStateException("shadow release must be reviewed");
        }
        runtime.activate(sourceId, release, groups, rollout, previous, shadow);
        release.publish(clock.instant());
        repository.update(release);
        if (previous != null && previous.status() == RuleReleaseStatus.PUBLISHED) {
            previous.retire(clock.instant());
            repository.update(previous);
        }
        repository.activateBinding(sourceId, id, groups, rollout, shadowReleaseId, clock.instant());
        audit.record(actor, "RULE_RELEASE_PUBLISHED", "RuleRelease", id,
                "{\"sourceId\":\"" + sourceId + "\"}");
        return release;
    }

    @Transactional
    public RuleRelease rollback(String activeReleaseId, String sourceId, String actor) {
        var binding = repository.binding(sourceId)
                .orElseThrow(() -> new IllegalStateException("source has no active rule binding"));
        if (!binding.activeReleaseId().equals(activeReleaseId) || binding.previousReleaseId() == null) {
            throw new IllegalStateException("release is not the active binding or has no rollback target");
        }
        RuleRelease active = required(binding.activeReleaseId());
        RuleRelease previous = required(binding.previousReleaseId());
        if (previous.status() != RuleReleaseStatus.RETIRED) {
            throw new IllegalStateException("previous release is not restorable");
        }
        runtime.activate(sourceId, previous, binding.ruleSets(), 100, active, null);
        active.retire(clock.instant());
        previous.restore(clock.instant());
        repository.update(active);
        repository.update(previous);
        repository.rollbackBinding(sourceId, clock.instant());
        audit.record(actor, "RULE_RELEASE_ROLLED_BACK", "RuleRelease", previous.releaseId(),
                "{\"sourceId\":\"" + sourceId + "\",\"fromReleaseId\":\"" + activeReleaseId + "\"}");
        return previous;
    }

    public List<RuleRelease> list() {
        return repository.list();
    }

    public List<RuleReleaseRepository.Binding> bindings() {
        return repository.listBindings();
    }

    private RuleRelease required(String id) {
        return repository.find(id).orElseThrow(() -> new IllegalArgumentException("rule release not found"));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
