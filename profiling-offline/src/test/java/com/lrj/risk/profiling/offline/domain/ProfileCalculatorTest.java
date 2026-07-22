package com.lrj.risk.profiling.offline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProfileCalculatorTest {
    @Test
    void calculatesRfmBehaviorRiskAndRelationshipFeaturesFromFacts() {
        Instant asOf = Instant.parse("2026-07-22T12:00:00Z");
        var profile = ProfileCalculator.calculate("c1", List.of(
                new TransactionFact("c1", 100, "x", Instant.parse("2026-07-22T01:00:00Z"), true),
                new TransactionFact("c1", 200, "y", Instant.parse("2026-07-21T12:00:00Z"), false)),
                asOf, ZoneOffset.UTC);
        assertEquals(300, profile.amount90d());
        assertEquals(2, profile.transactionCount90d());
        assertEquals(2, profile.uniqueCounterparties());
        assertEquals(0.5, profile.nightRatio());
        assertEquals(1, profile.fraudCount());
    }
}
