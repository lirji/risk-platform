package com.lrj.risk.fraud.gateway;

import java.util.List;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.lrj.risk.common.event.TransactionMessage;
import com.lrj.risk.feature.FeatureClient;
import com.lrj.risk.feature.FeatureSnapshot;
import com.lrj.risk.fraud.engine.FraudEngineService;
import com.lrj.risk.fraud.engine.model.RiskAssessment;
import com.lrj.risk.fraud.engine.model.TransactionEvent;
import com.lrj.risk.fraud.gateway.dto.RiskRequest;
import com.lrj.risk.fraud.gateway.dto.RiskResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 同步决策入口。链路: 拉特征 (一次取齐) → 规则引擎 → 映射风险等级返回。
 *
 * <p>这是 §3.6 同步链路的雏形: 特征预取后进引擎、引擎内零 IO。
 * 后续接入 MLeap 模型分 (双轨决策) 与超时/熔断降级。
 */
@RestController
@RequestMapping("/risk")
public class RiskController {

    private final FeatureClient featureClient;
    private final FraudEngineService engine;
    private final TransactionPublisher publisher;

    public RiskController(FeatureClient featureClient, FraudEngineService engine,
                          TransactionPublisher publisher) {
        this.featureClient = featureClient;
        this.engine = engine;
        this.publisher = publisher;
    }

    @PostMapping("/evaluate")
    @SentinelResource(value = SentinelConfig.RES_EVALUATE,
            fallback = "evaluateFallback", blockHandler = "evaluateBlocked")
    public RiskResponse evaluate(@RequestBody RiskRequest req) {
        long start = System.nanoTime();

        // 1) 预取特征 (按账号一次取齐; Redis 不可用时降级为空快照)
        FeatureSnapshot features = featureClient.fetch(req.getAccountNo());

        // 2) 组装交易事件
        TransactionEvent txn = new TransactionEvent();
        txn.setSourceId(req.getSourceId());
        txn.setChannel(req.getChannel());
        txn.setBizType(req.getBizType());
        txn.setAccountNo(req.getAccountNo());
        txn.setCounterpartyAccount(req.getCounterpartyAccount());
        txn.setAmount(req.getAmount());
        txn.setDeviceId(req.getDeviceId());
        txn.setIp(req.getIp());

        // 3) 规则引擎评估
        RiskAssessment assessment = engine.evaluate(txn, features);

        long costMs = (System.nanoTime() - start) / 1_000_000;

        // 4) 异步发布交易事件到 Kafka (off 关键路径, 失败不影响返回)
        publisher.publishAsync(toMessage(req));

        return new RiskResponse(
                assessment.getLevel().name(),
                assessment.getAction(),
                assessment.getFraudScore(),
                assessment.getHitRules(),
                costMs);
    }

    /** 流控/熔断触发: 快速返回保守决策 (加验证), 不拖垮 SLA。签名 = 原方法 + BlockException。 */
    public RiskResponse evaluateBlocked(RiskRequest req, BlockException ex) {
        return new RiskResponse("MEDIUM", "CHALLENGE", 0d,
                List.of("DEGRADED_RATELIMIT"), 0);
    }

    /** 业务异常降级: 返回保守"需复核"。签名 = 原方法 + Throwable。 */
    public RiskResponse evaluateFallback(RiskRequest req, Throwable ex) {
        return new RiskResponse("HIGH", "REVIEW", 0d,
                List.of("FALLBACK_EXCEPTION"), 0);
    }

    private TransactionMessage toMessage(RiskRequest req) {
        TransactionMessage m = new TransactionMessage();
        m.setSourceId(req.getSourceId());
        m.setChannel(req.getChannel());
        m.setBizType(req.getBizType());
        m.setAccountNo(req.getAccountNo());
        m.setCounterpartyAccount(req.getCounterpartyAccount());
        m.setAmount(req.getAmount());
        m.setDeviceId(req.getDeviceId());
        m.setIp(req.getIp());
        m.setEventTime(System.currentTimeMillis());
        return m;
    }
}
