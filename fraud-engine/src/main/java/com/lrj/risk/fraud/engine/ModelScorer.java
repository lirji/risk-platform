package com.lrj.risk.fraud.engine;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.lrj.risk.feature.FeatureSnapshot;
import com.lrj.risk.fraud.engine.model.TransactionEvent;

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
public class ModelScorer {

    private static final Logger log = LoggerFactory.getLogger(ModelScorer.class);
    private static final String MODEL_PATH = "model/fraud-rf.pmml";

    private volatile Evaluator evaluator;

    @PostConstruct
    void init() {
        try (InputStream is = new ClassPathResource(MODEL_PATH).getInputStream()) {
            this.evaluator = new LoadingModelEvaluatorBuilder().load(is).build();
            this.evaluator.verify();
            log.info("PMML 模型加载成功: {}", MODEL_PATH);
        } catch (Exception e) {
            log.warn("PMML 模型加载失败, 模型分恒为 0(退化为纯规则): {}", e.toString());
            this.evaluator = null;
        }
    }

    /** 返回欺诈概率 [0,1]; 模型不可用或异常时返回 0。 */
    public double score(TransactionEvent txn, FeatureSnapshot features) {
        Evaluator ev = this.evaluator;
        if (ev == null) {
            return 0d;
        }
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("amount", txn.getAmount() / 100.0);                         // 分 → 元 (训练用元)
            args.put("daily_amount", features.getLong("daily_amount") / 100.0);  // 分 → 元
            args.put("daily_count", (double) features.getLong("daily_count"));
            args.put("hour", (double) java.time.LocalTime.now().getHour());
            args.put("cross_bank", txn.getCounterpartyAccount() != null ? 1.0 : 0.0);
            args.put("device_new", features.getBoolean("device_new") ? 1.0 : 0.0);

            Map<String, ?> results = ev.evaluate(args);
            Object p1 = results.get("probability(1)");
            return p1 == null ? 0d : ((Number) EvaluatorUtil.decode(p1)).doubleValue();
        } catch (Exception e) {
            log.warn("模型评分异常, 降级返回 0: {}", e.toString());
            return 0d;
        }
    }
}
