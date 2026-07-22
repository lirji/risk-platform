package com.lrj.risk.fraud.engine;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import com.lrj.risk.feature.domain.FeatureSnapshot;
import com.lrj.risk.fraud.application.port.out.ModelScoringPort;
import com.lrj.risk.fraud.domain.model.TransactionEvent;
import com.lrj.risk.contracts.v1.ModelFeatureSchemaV1;

import jakarta.annotation.PostConstruct;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 随机森林模型评分 (PLAN M6b)。
 *
 * <p>启动时加载 PMML(由 fraud-model-train 用 Spark MLlib 训练导出), 进程内推理, 输出欺诈概率分。
 * 特征字段须与训练严格一致 (training-serving 对齐): amount/daily_amount 用元、device_new 等。
 * 任何加载/评分异常都降级返回 0 分, 退化为纯规则决策 (PLAN §3.6 / §4.3)。
 */
@Component
public class ModelScorer implements ModelScoringPort {

    private static final Logger log = LoggerFactory.getLogger(ModelScorer.class);
    private static final String MODEL_PATH = "model/fraud-rf.pmml";

    private volatile Deployment active;
    private volatile Deployment previous;
    private volatile int rolloutPercentage = 100;

    @PostConstruct
    void init() {
        try (InputStream is = new ClassPathResource(MODEL_PATH).getInputStream()) {
            reload(is.readAllBytes(), "fraud-rf-baseline-1");
        } catch (Exception e) {
            log.warn("PMML 模型加载失败, 模型分恒为 0(退化为纯规则): {}", e.toString());
            this.active = null;
            this.previous = null;
        }
    }

    /** 返回欺诈概率 [0,1]; 模型不可用或异常时返回 0。 */
    @Override
    public double score(TransactionEvent txn, FeatureSnapshot features) {
        Deployment deployment = select(txn);
        if (deployment == null) {
            throw new IllegalStateException("fraud model is unavailable");
        }
        try {
            Map<String, Object> args = inputFeatures(txn, features);

            Map<String, ?> results = deployment.evaluator().evaluate(args);
            Object p1 = results.get("probability(1)");
            return p1 == null ? 0d : ((Number) EvaluatorUtil.decode(p1)).doubleValue();
        } catch (Exception e) {
            throw new IllegalStateException("fraud model scoring failed", e);
        }
    }

    @Override
    public String activeVersion(TransactionEvent transaction) {
        Deployment deployment = select(transaction);
        return deployment == null ? "unavailable" : deployment.version();
    }

    public synchronized void reload(byte[] pmml, String newVersion) {
        deploy(pmml, newVersion, 100);
    }

    public synchronized void deploy(byte[] pmml, String newVersion, int rolloutPercentage) {
        if (newVersion == null || newVersion.isBlank()) {
            throw new IllegalArgumentException("model version is required");
        }
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be 0..100");
        }
        try (InputStream input = new ByteArrayInputStream(pmml)) {
            Evaluator candidate = new LoadingModelEvaluatorBuilder().load(input).build();
            candidate.verify();
            Deployment stable = this.previous != null ? this.previous : this.active;
            if (rolloutPercentage < 100 && stable == null) {
                throw new IllegalStateException("partial model rollout requires an active model");
            }
            this.previous = rolloutPercentage < 100 ? stable : null;
            this.active = new Deployment(candidate, newVersion);
            this.rolloutPercentage = rolloutPercentage;
            log.info("model atomically activated version={} rollout={}", newVersion, rolloutPercentage);
        } catch (Exception exception) {
            throw new IllegalStateException("PMML validation failed", exception);
        }
    }

    private Deployment select(TransactionEvent transaction) {
        Deployment candidate = active;
        Deployment stable = previous;
        if (stable != null && Math.floorMod(java.util.Objects.hash(
                transaction == null ? null : transaction.getSourceId(),
                transaction == null ? null : transaction.getTxnId()), 100) >= rolloutPercentage) {
            return stable;
        }
        return candidate;
    }

    private record Deployment(Evaluator evaluator, String version) { }

    Map<String, Object> inputFeatures(TransactionEvent txn, FeatureSnapshot features) {
        Map<String, Object> args = new HashMap<>();
        args.put("amount", txn.getAmount() / 100.0);
        args.put("daily_amount", features.getLong("daily_amount") / 100.0);
        args.put("daily_count", (double) features.getLong("daily_count"));
        int eventHour = txn.getEventTime() == null ? 0
                : txn.getEventTime().atZone(java.time.ZoneOffset.UTC).getHour();
        args.put("hour", (double) eventHour);
        args.put("cross_bank", txn.getCounterpartyAccount() != null ? 1.0 : 0.0);
        args.put("device_new", features.getBoolean("device_new") ? 1.0 : 0.0);
        if (!args.keySet().equals(new java.util.HashSet<>(ModelFeatureSchemaV1.FEATURES))) {
            throw new IllegalStateException("serving features differ from " + ModelFeatureSchemaV1.VERSION);
        }
        return args;
    }
}
