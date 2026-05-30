package com.lrj.risk.rating.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 评级模型 (一组评分规则 + 评级阈值)。对一个客户的标签: 累加命中规则分 → 总分映射评级。
 * 整体随 Spark 任务广播到各 executor, 故 Serializable。
 */
public class RatingModel implements Serializable {

    private final String modelCode;
    private final List<ScoreRule> rules;
    /** 评级阈值: min_score(降序) → grade, 用于"总分落入哪个等级"。 */
    private final TreeMap<Integer, String> gradeBands;

    public RatingModel(String modelCode, List<ScoreRule> rules, Map<Integer, String> gradeBands) {
        this.modelCode = modelCode;
        this.rules = rules;
        this.gradeBands = new TreeMap<>(gradeBands);
    }

    /** 对一份客户标签评分: 返回总分 + 命中规则码列表。 */
    public ScoreResult score(Map<String, Double> tags) {
        int total = 0;
        List<String> hit = new ArrayList<>();
        for (ScoreRule rule : rules) {
            Double v = tags.get(rule.getTagField());
            if (v != null && rule.matches(v)) {
                total += rule.getScore();
                hit.add(rule.getRuleCode());
            }
        }
        return new ScoreResult(total, grade(total), hit);
    }

    /** 总分映射评级: 取 ≤ 总分 的最大 min_score 对应等级。 */
    private String grade(int total) {
        Map.Entry<Integer, String> e = gradeBands.floorEntry(total);
        return e == null ? "A" : e.getValue();
    }

    public String getModelCode() {
        return modelCode;
    }

    /** 评分结果。 */
    public record ScoreResult(int score, String grade, List<String> hitRules) implements Serializable {
    }
}
