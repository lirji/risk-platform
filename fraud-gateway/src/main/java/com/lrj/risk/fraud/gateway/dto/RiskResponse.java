package com.lrj.risk.fraud.gateway.dto;

import java.util.List;

/** Stable synchronous decision response. */
public record RiskResponse(
        String decisionId,
        String txnId,
        String riskLevel,
        String action,
        double fraudScore,
        List<String> hitRules,
        String ruleVersion,
        String modelVersion,
        long costMs) {

    public RiskResponse {
        hitRules = List.copyOf(hitRules);
    }
}
