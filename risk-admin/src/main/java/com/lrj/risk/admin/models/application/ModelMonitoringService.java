package com.lrj.risk.admin.models.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.admin.models.domain.PopulationStabilityIndex;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelMonitoringService {
    private final ModelMonitoringPort monitoring;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ModelMonitoringService(ModelMonitoringPort monitoring, ObjectMapper mapper, Clock clock) {
        this.monitoring = monitoring;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<Map<String, Object>> latest(String modelId) {
        return monitoring.latest(modelId, 100);
    }

    @Transactional
    public double record(String modelId, Instant windowStart, Instant windowEnd,
                         long[] baselineHistogram, long[] currentHistogram) {
        if (!windowStart.isBefore(windowEnd)) {
            throw new IllegalArgumentException("windowStart must be before windowEnd");
        }
        double psi = PopulationStabilityIndex.calculate(baselineHistogram, currentHistogram);
        try {
            monitoring.save(modelId, windowStart, windowEnd,
                    java.util.Arrays.stream(currentHistogram).sum(),
                    mapper.writeValueAsString(currentHistogram), psi, clock.instant());
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("invalid histogram", failure);
        }
        return psi;
    }
}
