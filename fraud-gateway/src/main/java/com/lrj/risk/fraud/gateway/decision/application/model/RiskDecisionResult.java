package com.lrj.risk.fraud.gateway.decision.application.model;

import java.util.List;

public record RiskDecisionResult(
        String decisionId,
        String txnId,
        String riskLevel,
        String action,
        double fraudScore,
        List<String> hitRules,
        String ruleVersion,
        String modelVersion,
        long costMs) {

    public RiskDecisionResult {
        hitRules = List.copyOf(hitRules);
    }
}
