package com.lrj.risk.fraud.gateway.dto;

import java.util.List;

/**
 * 返回银行侧的决策结果。
 */
public class RiskResponse {

    private String riskLevel;     // LOW / MEDIUM / HIGH / REJECT
    private String action;        // ALLOW / CHALLENGE / REVIEW / REJECT
    private double fraudScore;    // 模型分 (二期接入, 当前 0)
    private List<String> hitRules; // 命中的规则
    private long costMs;          // 决策耗时 (毫秒)

    public RiskResponse(String riskLevel, String action, double fraudScore,
                        List<String> hitRules, long costMs) {
        this.riskLevel = riskLevel;
        this.action = action;
        this.fraudScore = fraudScore;
        this.hitRules = hitRules;
        this.costMs = costMs;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getAction() {
        return action;
    }

    public double getFraudScore() {
        return fraudScore;
    }

    public List<String> getHitRules() {
        return hitRules;
    }

    public long getCostMs() {
        return costMs;
    }
}
