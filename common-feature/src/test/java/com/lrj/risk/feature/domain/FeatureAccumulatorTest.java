package com.lrj.risk.feature.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneOffset;

import com.lrj.risk.contracts.v1.EventMetadataV1;
import com.lrj.risk.contracts.v1.TransactionEventV1;
import org.junit.jupiter.api.Test;

class FeatureAccumulatorTest {

    @Test
    void usesEventTimeAndIgnoresDuplicateEventIds() {
        FeatureAccumulator accumulator = new FeatureAccumulator();
        TransactionEventV1 event = event("e-1", "2026-07-21T23:59:00Z", 100, "d-1");
        accumulator.apply(event, ZoneOffset.UTC);
        var duplicate = accumulator.apply(event, ZoneOffset.UTC);
        assertEquals("20260721", duplicate.values().get("daily_stat_date"));
        assertEquals("1", duplicate.values().get("txn_count_5m"));
        assertEquals("100", duplicate.values().get("daily_amount"));
        assertEquals("1", duplicate.values().get("daily_count"));
    }

    @Test
    void latePreviousDayDoesNotResetCurrentDay() {
        FeatureAccumulator accumulator = new FeatureAccumulator();
        accumulator.apply(event("e-2", "2026-07-22T01:00:00Z", 200, "d-2"), ZoneOffset.UTC);
        var late = accumulator.apply(event("e-3", "2026-07-21T23:58:00Z", 900, "d-3"), ZoneOffset.UTC);
        assertEquals("20260722", late.values().get("daily_stat_date"));
        assertEquals("200", late.values().get("daily_amount"));
        assertEquals("1", late.values().get("daily_count"));
    }

    private TransactionEventV1 event(String eventId, String eventTime, long amount, String device) {
        Instant timestamp = Instant.parse(eventTime);
        return new TransactionEventV1(new EventMetadataV1(eventId, "c", "s", eventId, timestamp, 1),
                "MOBILE", "TRANSFER", "account", "counterparty", amount, "CNY", device,
                "127.0.0.1", timestamp);
    }
}
