package com.lrj.risk.admin.operations.adapter;

import java.util.List;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Database-backed operational gauges. They represent current state, not an ever-growing event counter. */
@Component
public class OperationsMetrics {
    private final JdbcTemplate jdbc;
    private final MultiGauge ratingJobs;
    private final MultiGauge modelDeployments;

    public OperationsMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("risk.dead.events", () -> count("SELECT COUNT(*) FROM outbox_event WHERE status='DEAD'"))
                .tag("kind", "outbox").description("Current dead Outbox events").register(registry);
        Gauge.builder("risk.dead.events", () -> count("SELECT COUNT(*) FROM dead_letter_event WHERE status='DEAD'"))
                .tag("kind", "kafka_dlt").description("Current catalogued Kafka DLT events").register(registry);
        Gauge.builder("risk.model.psi.max", () -> decimal("SELECT COALESCE(MAX(psi), 0) FROM model_monitor_snapshot"))
                .description("Maximum recorded model PSI").register(registry);
        ratingJobs = MultiGauge.builder("risk.rating.jobs").description("Rating jobs by current status")
                .register(registry);
        modelDeployments = MultiGauge.builder("risk.model.deployments")
                .description("Model versions by deployment status").register(registry);
    }

    @PostConstruct
    void initialRefresh() {
        refreshStateGauges();
    }

    @Scheduled(fixedDelayString = "${risk.operations.metrics-refresh-ms:30000}")
    void refreshStateGauges() {
        ratingJobs.register(rows("SELECT status, COUNT(*) AS value FROM rating_job GROUP BY status"), true);
        modelDeployments.register(rows("SELECT status, COUNT(*) AS value FROM model_version GROUP BY status"), true);
    }

    private List<MultiGauge.Row<?>> rows(String sql) {
        try {
            return jdbc.queryForList(sql).stream()
                    .<MultiGauge.Row<?>>map(row -> MultiGauge.Row.of(
                            Tags.of("status", String.valueOf(row.get("status"))),
                            ((Number) row.get("value")).doubleValue()))
                    .toList();
        } catch (DataAccessException unavailable) {
            return List.of();
        }
    }

    private double count(String sql) {
        try {
            Number value = jdbc.queryForObject(sql, Number.class);
            return value == null ? 0 : value.doubleValue();
        } catch (DataAccessException unavailable) {
            return Double.NaN;
        }
    }

    private double decimal(String sql) {
        return count(sql);
    }
}
