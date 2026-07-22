package com.lrj.risk.fraud.gateway.decision.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.lrj.risk.fraud.gateway.decision.application.model.RiskDecisionResult;

/** Durable synchronous decision aggregate. */
public record DecisionRecord(
        String decisionId,
        String sourceId,
        String txnId,
        String correlationId,
        Instant eventTime,
        String accountToken,
        String riskLevel,
        String action,
        double fraudScore,
        List<String> hitRules,
        Map<String, String> featureSnapshot,
        String ruleVersion,
        String modelVersion,
        long costMs,
        Instant createdAt) {

    public DecisionRecord {
        hitRules = List.copyOf(hitRules);
        featureSnapshot = Map.copyOf(featureSnapshot);
    }

    public RiskDecisionResult toResult() {
        return new RiskDecisionResult(decisionId, txnId, riskLevel, action, fraudScore,
                hitRules, ruleVersion, modelVersion, costMs);
    }

    public DecisionRecord withCostMs(long measuredCostMs) {
        return new DecisionRecord(decisionId, sourceId, txnId, correlationId, eventTime, accountToken,
                riskLevel, action, fraudScore, hitRules, featureSnapshot, ruleVersion, modelVersion,
                measuredCostMs, createdAt);
    }
}
