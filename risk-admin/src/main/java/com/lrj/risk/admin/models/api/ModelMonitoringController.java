package com.lrj.risk.admin.models.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.models.application.ModelMonitoringService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models/{modelId}/monitoring")
public class ModelMonitoringController {
    private final ModelMonitoringService monitoring;

    public ModelMonitoringController(ModelMonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping
    List<Map<String, Object>> list(@PathVariable String modelId) {
        return monitoring.latest(modelId);
    }

    @PostMapping
    Map<String, Double> record(@PathVariable String modelId, @Valid @RequestBody SnapshotRequest request) {
        double psi = monitoring.record(modelId, request.windowStart(), request.windowEnd(),
                request.baselineHistogram(), request.currentHistogram());
        return Map.of("psi", psi);
    }

    public record SnapshotRequest(@NotNull Instant windowStart, @NotNull Instant windowEnd,
                                  long[] baselineHistogram, long[] currentHistogram) { }
}
