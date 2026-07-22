package com.lrj.risk.admin.models.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface ModelMonitoringPort {
    List<Map<String, Object>> latest(String modelId, int limit);

    void save(String modelId, Instant windowStart, Instant windowEnd, long sampleCount,
              String histogramJson, double psi, Instant createdAt);
}
