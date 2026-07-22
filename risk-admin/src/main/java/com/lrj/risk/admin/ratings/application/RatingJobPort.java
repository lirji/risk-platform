package com.lrj.risk.admin.ratings.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface RatingJobPort {
    List<Map<String, Object>> findAll();

    void create(String jobId, String modelCode, String sourceIndex, String targetIndex,
                String createdBy, Instant createdAt);

    boolean retry(String jobId);
}
