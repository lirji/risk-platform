package com.lrj.risk.admin.rules.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RuleReleaseTest {

    @Test
    void enforcesReviewAndSeparationOfDuties() {
        Instant now = Instant.parse("2026-07-22T00:00:00Z");
        RuleRelease release = new RuleRelease("id", "code", "name", 1, "drl", "hash",
                "author", RuleReleaseStatus.DRAFT, null, now, now);
        assertThrows(IllegalStateException.class, () -> release.approve("reviewer", now));
        release.submit(now);
        assertThrows(IllegalStateException.class, () -> release.approve("author", now));
        release.approve("reviewer", now);
        release.publish(now);
        assertEquals(RuleReleaseStatus.PUBLISHED, release.status());
    }
}
