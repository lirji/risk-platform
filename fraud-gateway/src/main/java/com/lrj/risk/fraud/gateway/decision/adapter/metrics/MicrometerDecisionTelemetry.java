package com.lrj.risk.fraud.gateway.decision.adapter.metrics;

import java.time.Duration;

import com.lrj.risk.fraud.gateway.decision.application.port.out.DecisionTelemetry;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MicrometerDecisionTelemetry implements DecisionTelemetry {
    private final MeterRegistry registry;
    private final DistributionSummary modelScores;

    public MicrometerDecisionTelemetry(MeterRegistry registry) {
        this.registry = registry;
        this.modelScores = DistributionSummary.builder("risk.model.score")
                .description("Fraud model score distribution")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Override
    public void stageDuration(String stage, long nanoseconds) {
        registry.timer("risk.decision.stage.duration", "stage", stage)
                .record(Duration.ofNanos(Math.max(0, nanoseconds)));
    }

    @Override
    public void decision(String riskLevel, String action, double score, int ruleHitCount, boolean degraded) {
        registry.counter("risk.decision.total", "risk_level", riskLevel, "action", action,
                "degraded", Boolean.toString(degraded)).increment();
        registry.counter("risk.rule.hit.total", "hit", ruleHitCount > 0 ? "true" : "false").increment();
        modelScores.record(score);
    }
}
