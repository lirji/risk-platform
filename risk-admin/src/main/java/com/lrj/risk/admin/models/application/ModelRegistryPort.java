package com.lrj.risk.admin.models.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ModelRegistryPort {
    void insert(String id, String code, int version, String artifactUri, String checksum,
                String metricsJson, String dataVersion, String actor, Instant createdAt);

    boolean approve(String id, String reviewer, Instant updatedAt);

    Optional<Activation> deploymentCandidate(String id);

    Optional<Activation> rollbackTarget(String id);

    boolean hasServingDeployment(String modelCode);

    void prepareDeployment(String modelCode, String candidateId, int rolloutPercentage, Instant updatedAt);

    boolean activate(String id, String expectedStatus, String targetStatus,
                     int rolloutPercentage, Instant updatedAt);

    List<Map<String, Object>> findAll();

    record Activation(String modelCode, int version, String artifactUri, String checksum, String status) { }
}
