package com.lrj.risk.fraud.engine.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险评估结果 (规则引擎的输出 Fact)。
 *
 * <p>规则命中时调用 {@link #escalate} 抬升风险等级并登记命中规则;
 * 各规则顺序无关 (只升不降), 故无需 update/modify, 避免 Drools 死循环 (见 drools-demo 踩坑3)。
 * {@code fraudScore} 预留给二期模型分接入 (双轨决策, PLAN §4.3), 当前默认 0。
 */
public class RiskAssessment {

    private RiskLevel level = RiskLevel.LOW;
    private final List<String> hitRules = new ArrayList<>();
    private double fraudScore = 0d;

    /** 抬升风险等级 (只升不降) 并登记命中规则。 */
    public void escalate(RiskLevel candidate, String hitRule) {
        if (candidate.ordinal() > level.ordinal()) {
            level = candidate;
        }
        hitRules.add(hitRule);
    }

    /** 直接拒绝 (硬规则命中)。 */
    public void reject(String hitRule) {
        escalate(RiskLevel.REJECT, hitRule);
    }

    /** 由风险等级映射到对银行返回的处置动作。 */
    public String getAction() {
        return switch (level) {
            case LOW -> "ALLOW";       // 放行
            case MEDIUM -> "CHALLENGE"; // 加验证
            case HIGH -> "REVIEW";      // 人工复核
            case REJECT -> "REJECT";    // 拦截
        };
    }

    public RiskLevel getLevel() {
        return level;
    }

    public List<String> getHitRules() {
        return hitRules;
    }

    public double getFraudScore() {
        return fraudScore;
    }

    public void setFraudScore(double fraudScore) {
        this.fraudScore = fraudScore;
    }
}
