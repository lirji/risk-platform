package com.lrj.risk.admin.models.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.admin.shared.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelRegistryService {

    private final ModelRegistryPort models;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;
    private final ModelRuntimePort runtime;

    public ModelRegistryService(ModelRegistryPort models, ObjectMapper mapper, AuditService audit, Clock clock,
                                ModelRuntimePort runtime) {
        this.models = models;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
        this.runtime = runtime;
    }

    @Transactional
    public String register(String code, int version, String artifactUri, String checksum,
                           Map<String, Double> metrics, String dataVersion, String actor) {
        requireMetrics(metrics);
        String id = UUID.randomUUID().toString();
        models.insert(id, code, version, artifactUri, checksum, json(metrics), dataVersion,
                actor, clock.instant());
        audit.record(actor, "MODEL_REGISTERED", "ModelVersion", id, "{}");
        return id;
    }

    @Transactional
    public void approve(String id, String reviewer) {
        if (!models.approve(id, reviewer, clock.instant())) {
            throw new IllegalStateException("model is not reviewable or self-approval attempted");
        }
        audit.record(reviewer, "MODEL_APPROVED", "ModelVersion", id, "{}");
    }

    @Transactional
    public void activate(String id, String actor) {
        activate(id, actor, 100);
    }

    @Transactional
    public void activate(String id, String actor, int rolloutPercentage) {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be 0..100");
        }
        ModelRegistryPort.Activation candidate = models.deploymentCandidate(id)
                .orElseThrow(() -> new IllegalStateException("model is not approved or canary"));
        if (rolloutPercentage < 100 && !models.hasServingDeployment(candidate.modelCode())) {
            throw new IllegalStateException("partial model rollout requires an active model");
        }
        runtime.activate(candidate.modelCode() + "-" + candidate.version(), candidate.artifactUri(),
                candidate.checksum(), rolloutPercentage);
        String code = candidate.modelCode();
        var now = clock.instant();
        models.prepareDeployment(code, id, rolloutPercentage, now);
        String targetStatus = rolloutPercentage < 100 ? "CANARY" : "ACTIVE";
        if (!models.activate(id, candidate.status(), targetStatus, rolloutPercentage, now)) {
            throw new IllegalStateException("model activation conflict");
        }
        audit.record(actor, "MODEL_ACTIVATED", "ModelVersion", id, "{}");
    }

    @Transactional
    public void rollback(String id, String actor) {
        ModelRegistryPort.Activation target = models.rollbackTarget(id)
                .orElseThrow(() -> new IllegalStateException("model is not a reviewed rollback target"));
        runtime.activate(target.modelCode() + "-" + target.version(), target.artifactUri(), target.checksum(), 100);
        var now = clock.instant();
        models.prepareDeployment(target.modelCode(), id, 100, now);
        if (!models.activate(id, target.status(), "ACTIVE", 100, now)) {
            throw new IllegalStateException("model rollback conflict");
        }
        audit.record(actor, "MODEL_ROLLED_BACK", "ModelVersion", id, "{}");
    }

    public List<Map<String, Object>> list() {
        return models.findAll();
    }

    private void requireMetrics(Map<String, Double> metrics) {
        for (String required : List.of("auc", "ks", "recallAtFixedFpr", "precision", "recall")) {
            if (!metrics.containsKey(required) || !Double.isFinite(metrics.get(required))) {
                throw new IllegalArgumentException("missing finite metric " + required);
            }
        }
        if (metrics.get("auc") < 0.5 || metrics.get("auc") > 1) {
            throw new IllegalArgumentException("auc must be between 0.5 and 1");
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
}
