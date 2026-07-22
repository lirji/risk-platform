package com.lrj.risk.fraud.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Decision aggregate used by rule and model policies; severity can only increase. */
public class RiskAssessment {

    private RiskLevel level = RiskLevel.LOW;
    private final List<String> hitRules = new ArrayList<>();
    private double fraudScore;

    public void escalate(RiskLevel candidate, String hitRule) {
        if (candidate.ordinal() > level.ordinal()) {
            level = candidate;
        }
        if (hitRule != null && !hitRule.isBlank() && !hitRules.contains(hitRule)) {
            hitRules.add(hitRule);
        }
    }

    public void reject(String hitRule) {
        escalate(RiskLevel.REJECT, hitRule);
    }

    public void tier(String hitRule) {
        escalate(RiskLevel.HIGH, hitRule);
    }

    public String getAction() {
        return switch (level) {
            case LOW -> "ALLOW";
            case MEDIUM -> "CHALLENGE";
            case HIGH -> "REVIEW";
            case REJECT -> "REJECT";
        };
    }

    public RiskLevel getLevel() {
        return level;
    }

    public List<String> getHitRules() {
        return Collections.unmodifiableList(hitRules);
    }

    public double getFraudScore() {
        return fraudScore;
    }

    public void setFraudScore(double fraudScore) {
        if (fraudScore < 0 || fraudScore > 1) {
            throw new IllegalArgumentException("fraudScore must be between 0 and 1");
        }
        this.fraudScore = fraudScore;
    }
}
