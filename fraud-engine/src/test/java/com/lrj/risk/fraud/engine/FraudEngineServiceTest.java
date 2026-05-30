package com.lrj.risk.fraud.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.lrj.risk.feature.FeatureSnapshot;
import com.lrj.risk.fraud.engine.model.RiskAssessment;
import com.lrj.risk.fraud.engine.model.RiskLevel;
import com.lrj.risk.fraud.engine.model.TransactionEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 真正编译 DRL (classpath KieContainer) 并触发规则, 验证规则集与决策聚合。
 * 这是 DRL 唯一可靠的语法/逻辑校验 (mvn compile 不校验)。
 */
class FraudEngineServiceTest {

    private static FraudEngineService engine;

    @BeforeAll
    static void setUp() {
        // 走与 @Bean 同一条构建路径 (程序化 KieFileSystem), 不起 Spring 容器。
        // 模型分用未初始化的 ModelScorer(恒返回 0), 隔离测试规则逻辑。
        engine = new FraudEngineService(
                new KieContainerHolder(),
                new SourceRuleSetBinding(),
                new ModelScorer());
    }

    private TransactionEvent txn(String bizType, long amount) {
        TransactionEvent t = new TransactionEvent();
        t.setSourceId("MOBILE_TRANSFER");
        t.setBizType(bizType);
        t.setAccountNo("ACC001");
        t.setAmount(amount);
        return t;
    }

    @Test
    void 正常小额交易_放行() {
        RiskAssessment r = engine.evaluate(txn("TRANSFER", 10000), FeatureSnapshot.empty());
        assertEquals(RiskLevel.LOW, r.getLevel());
        assertEquals("ALLOW", r.getAction());
        assertTrue(r.getHitRules().isEmpty());
    }

    @Test
    void 大额转账_命中HIGH() {
        // 60000 元 = 6_000_000 分 > 5_000_000 阈值
        RiskAssessment r = engine.evaluate(txn("TRANSFER", 6_000_000), FeatureSnapshot.empty());
        assertEquals(RiskLevel.HIGH, r.getLevel());
        assertEquals("REVIEW", r.getAction());
        assertTrue(r.getHitRules().contains("LARGE_TRANSFER"));
    }

    @Test
    void 黑名单账户_直接拒绝() {
        FeatureSnapshot f = new FeatureSnapshot(Map.of("blacklist", "true"));
        RiskAssessment r = engine.evaluate(txn("TRANSFER", 10000), f);
        assertEquals(RiskLevel.REJECT, r.getLevel());
        assertEquals("REJECT", r.getAction());
        assertTrue(r.getHitRules().contains("BLACKLIST_ACCOUNT"));
    }

    @Test
    void 当日累计超限_命中HIGH() {
        FeatureSnapshot f = new FeatureSnapshot(Map.of("daily_amount", "20000000"));
        RiskAssessment r = engine.evaluate(txn("TRANSFER", 10000), f);
        assertEquals(RiskLevel.HIGH, r.getLevel());
        assertTrue(r.getHitRules().contains("DAILY_AMOUNT_EXCEEDED"));
    }

    @Test
    void 决策表_手机大额命中() {
        TransactionEvent t = txn("TRANSFER", 8_000_000); // 8万元, 落在 [5万,100万) 档
        t.setChannel("MOBILE");
        RiskAssessment r = engine.evaluate(t, FeatureSnapshot.empty());
        assertTrue(r.getHitRules().contains("DT_MOBILE_LARGE"),
                "应命中决策表规则 DT_MOBILE_LARGE, 实际=" + r.getHitRules());
    }

    @Test
    void 短时高频_命中速度规则() {
        FeatureSnapshot f = new FeatureSnapshot(Map.of("txn_count_5m", "6"));
        RiskAssessment r = engine.evaluate(txn("TRANSFER", 10000), f);
        assertTrue(r.getHitRules().contains("HIGH_VELOCITY"),
                "应命中速度规则 HIGH_VELOCITY, 实际=" + r.getHitRules());
    }

    @Test
    void 黑名单优先于阈值_取最高等级() {
        // 同时命中黑名单(REJECT) + 大额(HIGH), 最终应为 REJECT
        FeatureSnapshot f = new FeatureSnapshot(Map.of("blacklist", "true"));
        RiskAssessment r = engine.evaluate(txn("TRANSFER", 6_000_000), f);
        assertEquals(RiskLevel.REJECT, r.getLevel());
        assertTrue(r.getHitRules().contains("BLACKLIST_ACCOUNT"));
        assertTrue(r.getHitRules().contains("LARGE_TRANSFER"));
    }
}
