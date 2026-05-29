package com.lrj.risk.fraud.engine.model;

/**
 * 风险等级, 按严重程度升序声明 (ordinal 越大越严重)。
 * 决策聚合时取所有命中规则的最高等级 (见 {@link RiskAssessment#escalate}).
 */
public enum RiskLevel {
    /** 放行 */
    LOW,
    /** 加验证 / 关注 */
    MEDIUM,
    /** 人工复核 */
    HIGH,
    /** 直接拒绝 */
    REJECT
}
