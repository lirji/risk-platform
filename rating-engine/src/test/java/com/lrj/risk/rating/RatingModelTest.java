package com.lrj.risk.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.lrj.risk.rating.config.RatingModel;
import com.lrj.risk.rating.config.ScoreRule;

import org.junit.jupiter.api.Test;

/**
 * 验证评分评级逻辑 (与 MySQL 种子模型 RISK_M1 同口径), 不依赖 Spark/ES/MySQL。
 */
class RatingModelTest {

    private RatingModel m1() {
        List<ScoreRule> rules = List.of(
                new ScoreRule("HIGH_AMOUNT_90D", "amount_90d", "GT", 1000000, 30),
                new ScoreRule("HIGH_FREQ_90D", "txn_cnt_90d", "GT", 200, 20),
                new ScoreRule("MANY_COUNTERPART", "counterparty", "GT", 50, 20),
                new ScoreRule("LOW_BALANCE", "avg_balance", "LT", 1000, 15),
                new ScoreRule("NIGHT_ACTIVE", "night_ratio", "GT", 0.5, 15));
        Map<Integer, String> grades = Map.of(70, "D", 45, "C", 20, "B", 0, "A");
        return new RatingModel("RISK_M1", rules, grades);
    }

    @Test
    void 高风险客户_全命中_评D() {
        // C001: 命中全部 5 条 = 30+20+20+15+15 = 100 → D
        RatingModel.ScoreResult r = m1().score(Map.of(
                "amount_90d", 5000000.0, "txn_cnt_90d", 350.0, "counterparty", 80.0,
                "avg_balance", 500.0, "night_ratio", 0.7));
        assertEquals(100, r.score());
        assertEquals("D", r.grade());
        assertEquals(5, r.hitRules().size());
    }

    @Test
    void 中风险客户_部分命中_评B() {
        // C002: 仅命中 HIGH_AMOUNT_90D(30) → 落 [20,45) → B
        RatingModel.ScoreResult r = m1().score(Map.of(
                "amount_90d", 1200000.0, "txn_cnt_90d", 60.0, "counterparty", 10.0,
                "avg_balance", 8000.0, "night_ratio", 0.2));
        assertEquals(30, r.score());
        assertEquals("B", r.grade());
        assertTrue(r.hitRules().contains("HIGH_AMOUNT_90D"));
    }

    @Test
    void 低风险客户_无命中_评A() {
        // C003: 全不命中 → 0 → A
        RatingModel.ScoreResult r = m1().score(Map.of(
                "amount_90d", 30000.0, "txn_cnt_90d", 5.0, "counterparty", 2.0,
                "avg_balance", 50000.0, "night_ratio", 0.05));
        assertEquals(0, r.score());
        assertEquals("A", r.grade());
        assertTrue(r.hitRules().isEmpty());
    }
}
