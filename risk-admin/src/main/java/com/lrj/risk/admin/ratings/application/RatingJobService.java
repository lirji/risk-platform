package com.lrj.risk.admin.ratings.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lrj.risk.admin.shared.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RatingJobService {
    private final RatingJobPort jobs;
    private final AuditService audit;
    private final Clock clock;

    public RatingJobService(RatingJobPort jobs, AuditService audit, Clock clock) {
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
    }

    public List<Map<String, Object>> list() {
        return jobs.findAll();
    }

    @Transactional
    public String create(String modelCode, String sourceIndex, String targetIndex, String actor) {
        String id = UUID.randomUUID().toString();
        jobs.create(id, modelCode, sourceIndex, targetIndex, actor, clock.instant());
        audit.record(actor, "RATING_JOB_CREATED", "RatingJob", id, "{}");
        return id;
    }

    @Transactional
    public void retry(String id, String actor) {
        if (!jobs.retry(id)) throw new IllegalStateException("rating job is not retryable");
        audit.record(actor, "RATING_JOB_RETRIED", "RatingJob", id, "{}");
    }
}
