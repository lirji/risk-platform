package com.lrj.risk.rating.config;

import java.io.Serializable;

/**
 * 一条评分规则 (来自 MySQL t_score_rule): 标签字段 比较符 阈值 → 命中加分。
 * Serializable 以便随 Spark 任务分发到 executor。
 */
public class ScoreRule implements Serializable {

    private final String ruleCode;
    private final String tagField;
    private final String operator;   // GT/GE/LT/LE/EQ
    private final double threshold;
    private final int score;

    public ScoreRule(String ruleCode, String tagField, String operator, double threshold, int score) {
        this.ruleCode = ruleCode;
        this.tagField = tagField;
        this.operator = operator;
        this.threshold = threshold;
        this.score = score;
    }

    /** 标签值是否命中本规则。 */
    public boolean matches(double tagValue) {
        return switch (operator) {
            case "GT" -> tagValue > threshold;
            case "GE" -> tagValue >= threshold;
            case "LT" -> tagValue < threshold;
            case "LE" -> tagValue <= threshold;
            case "EQ" -> tagValue == threshold;
            default -> false;
        };
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getTagField() {
        return tagField;
    }

    public int getScore() {
        return score;
    }
}
