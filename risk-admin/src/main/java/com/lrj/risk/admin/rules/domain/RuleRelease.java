package com.lrj.risk.admin.rules.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.Objects;

public class RuleRelease {

    private final String releaseId;
    private final String ruleCode;
    private final String ruleName;
    private final int version;
    private final String drl;
    private final String checksum;
    private final String authorId;
    private RuleReleaseStatus status;
    private String reviewerId;
    private final Instant createdAt;
    private Instant updatedAt;

    public RuleRelease(String releaseId, String ruleCode, String ruleName, int version, String drl,
                       String checksum, String authorId, RuleReleaseStatus status, String reviewerId,
                       Instant createdAt, Instant updatedAt) {
        this.releaseId = required(releaseId, "releaseId");
        this.ruleCode = required(ruleCode, "ruleCode");
        this.ruleName = required(ruleName, "ruleName");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
        this.drl = required(drl, "drl");
        this.checksum = required(checksum, "checksum");
        this.authorId = required(authorId, "authorId");
        this.status = status;
        this.reviewerId = reviewerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void submit(Instant now) {
        requireStatus(RuleReleaseStatus.DRAFT);
        status = RuleReleaseStatus.IN_REVIEW;
        updatedAt = now;
    }

    public void approve(String reviewer, Instant now) {
        requireStatus(RuleReleaseStatus.IN_REVIEW);
        if (authorId.equals(reviewer)) {
            throw new IllegalStateException("author cannot approve their own release");
        }
        reviewerId = required(reviewer, "reviewer");
        status = RuleReleaseStatus.APPROVED;
        updatedAt = now;
    }

    public void publish(Instant now) {
        requireStatus(RuleReleaseStatus.APPROVED);
        status = RuleReleaseStatus.PUBLISHED;
        updatedAt = now;
    }

    public void retire(Instant now) {
        if (status != RuleReleaseStatus.PUBLISHED) {
            throw new IllegalStateException("only published release can be retired");
        }
        status = RuleReleaseStatus.RETIRED;
        updatedAt = now;
    }

    public void restore(Instant now) {
        requireStatus(RuleReleaseStatus.RETIRED);
        status = RuleReleaseStatus.PUBLISHED;
        updatedAt = now;
    }

    private void requireStatus(RuleReleaseStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("expected " + expected + " but was " + status);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public String releaseId() { return releaseId; }
    public String ruleCode() { return ruleCode; }
    public String ruleName() { return ruleName; }
    public int version() { return version; }
    public String drl() { return drl; }
    public String checksum() { return checksum; }
    public String authorId() { return authorId; }
    public RuleReleaseStatus status() { return status; }
    public String reviewerId() { return reviewerId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public String getReleaseId() { return releaseId; }
    public String getRuleCode() { return ruleCode; }
    public String getRuleName() { return ruleName; }
    public int getVersion() { return version; }
    @JsonIgnore public String getDrl() { return drl; }
    public String getChecksum() { return checksum; }
    public String getAuthorId() { return authorId; }
    public RuleReleaseStatus getStatus() { return status; }
    public String getReviewerId() { return reviewerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RuleRelease release
                && releaseId.equals(release.releaseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(releaseId);
    }
}
