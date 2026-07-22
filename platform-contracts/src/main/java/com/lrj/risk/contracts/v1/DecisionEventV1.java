package com.lrj.risk.contracts.v1;

import java.util.List;
import java.util.Map;

/** Immutable decision projection input; sensitive fields must already be masked by the producer. */
public record DecisionEventV1(
        EventMetadataV1 metadata,
        String decisionId,
        DecisionRiskLevel riskLevel,
        DecisionAction action,
        double fraudScore,
        List<RuleHitV1> hitRules,
        Map<String, String> featureSnapshot,
        String ruleVersion,
        String modelVersion,
        long costMs) {

    public DecisionEventV1 {
        ContractValidation.required(metadata, "metadata");
        ContractValidation.text(decisionId, "decisionId");
        ContractValidation.required(riskLevel, "riskLevel");
        ContractValidation.required(action, "action");
        hitRules = hitRules == null ? List.of() : List.copyOf(hitRules);
        featureSnapshot = featureSnapshot == null ? Map.of() : Map.copyOf(featureSnapshot);
        ContractValidation.text(ruleVersion, "ruleVersion");
        ContractValidation.text(modelVersion, "modelVersion");
        if (fraudScore < 0 || fraudScore > 1) {
            throw new IllegalArgumentException("fraudScore must be between 0 and 1");
        }
        if (costMs < 0) {
            throw new IllegalArgumentException("costMs must not be negative");
        }
    }
}
