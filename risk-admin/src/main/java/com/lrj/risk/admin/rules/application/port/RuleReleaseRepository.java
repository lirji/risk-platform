package com.lrj.risk.admin.rules.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.lrj.risk.admin.rules.domain.RuleRelease;

public interface RuleReleaseRepository {
    int nextVersion(String ruleCode);
    void insert(RuleRelease release);
    void update(RuleRelease release);
    Optional<RuleRelease> find(String releaseId);
    List<RuleRelease> list();
    void activateBinding(String sourceId, String releaseId, List<String> ruleSets,
                         int rolloutPercentage, String shadowReleaseId, Instant now);
    Optional<Binding> binding(String sourceId);
    List<Binding> listBindings();
    void rollbackBinding(String sourceId, Instant now);

    record Binding(String sourceId, String activeReleaseId, String previousReleaseId,
                   List<String> ruleSets, int rolloutPercentage, String shadowReleaseId) { }
}
