package com.lrj.risk.fraud.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.time.Instant;
import com.lrj.risk.contracts.v1.ModelFeatureSchemaV1;

import com.lrj.risk.feature.domain.FeatureSnapshot;
import com.lrj.risk.fraud.application.FraudDecisionService;
import com.lrj.risk.fraud.domain.model.RiskAssessment;
import com.lrj.risk.fraud.domain.model.TransactionEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 验证 M6 模型层: PMML 真实加载 + 推理出概率分 + 双轨决策(模型分进 Drools)。
 * 依赖 fraud-model-train 已导出 model/fraud-rf.pmml 到资源目录。
 */
class FraudModelScoringTest {

    private static ModelScorer scorer;
    private static FraudDecisionService engine;

    @BeforeAll
    static void setUp() {
        scorer = new ModelScorer();
        scorer.init();   // 真正加载 PMML
        engine = new FraudDecisionService(
                new DroolsRuleEvaluator(new KieContainerHolder()),
                new SourceRuleSetBinding(), scorer);
    }

    private TransactionEvent txn(long amountCents, String counterparty) {
        TransactionEvent t = new TransactionEvent();
        t.setTxnId("txn-model-test");
        t.setSourceId("MOBILE_TRANSFER");
        t.setBizType("TRANSFER");
        t.setAccountNo("ACC900");
        t.setAmount(amountCents);
        t.setCounterpartyAccount(counterparty);
        return t;
    }

    @Test
    void 低风险交易_模型分低() {
        // 小额、无跨行、无新设备 → 概率应较低
        double s = scorer.score(txn(50_000, null), FeatureSnapshot.empty());
        assertTrue(s >= 0.0 && s < 0.6, "低风险模型分应 <0.6, 实际=" + s);
    }

    @Test
    void 高风险特征_模型分升高且进双轨决策() {
        // 大额 + 大额当日累计 + 新设备 + 跨行 → 概率应明显更高
        FeatureSnapshot f = new FeatureSnapshot(Map.of(
                "daily_amount", "30000000",   // 30万元
                "daily_count", "15",
                "device_new", "true"));
        TransactionEvent t = txn(20_000_000, "6217000099887766");  // 20万元 + 跨行

        double s = scorer.score(t, f);
        assertTrue(s > 0.6, "高风险模型分应 >0.6, 实际=" + s);

        // 双轨: 模型分进 Drools 影响最终等级
        RiskAssessment r = engine.evaluate(t, f);
        assertNotNull(r.getLevel());
        boolean modelRuleHit = r.getHitRules().contains("MODEL_HIGH_RISK")
                || r.getHitRules().contains("MODEL_MID_RISK");
        assertTrue(modelRuleHit, "应命中模型规则, 实际命中=" + r.getHitRules());
    }

    @Test
    void 训练服务特征契约一致且hour使用事件时间() {
        TransactionEvent transaction = txn(50_000, null);
        transaction.setEventTime(Instant.parse("2026-07-22T23:15:00Z"));
        Map<String, Object> inputs = scorer.inputFeatures(transaction, FeatureSnapshot.empty());
        assertTrue(inputs.keySet().containsAll(ModelFeatureSchemaV1.FEATURES));
        assertTrue(ModelFeatureSchemaV1.FEATURES.containsAll(inputs.keySet()));
        assertTrue(Double.valueOf(23d).equals(inputs.get("hour")));
    }

    @Test
    void 模型灰度按交易稳定分桶并支持全量切换() throws Exception {
        byte[] pmml;
        try (var input = new org.springframework.core.io.ClassPathResource("model/fraud-rf.pmml").getInputStream()) {
            pmml = input.readAllBytes();
        }
        TransactionEvent transaction = txn(50_000, null);
        scorer.deploy(pmml, "fraud-rf-canary", 0);
        assertTrue("fraud-rf-baseline-1".equals(scorer.activeVersion(transaction)));
        assertTrue("fraud-rf-baseline-1".equals(scorer.activeVersion(transaction)));
        scorer.deploy(pmml, "fraud-rf-v2", 100);
        assertTrue("fraud-rf-v2".equals(scorer.activeVersion(transaction)));
    }
}
