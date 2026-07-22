package com.lrj.risk.profiling.flink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.v1.EventMetadataV1;
import com.lrj.risk.contracts.v1.TransactionEventV1;
import com.lrj.risk.feature.domain.FeatureAccumulator;
import org.junit.jupiter.api.Test;

class FeatureParityTest {

    @Test
    void serializedContractAndSharedAccumulatorProduceExpectedFixture() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        FeatureAccumulator lightweightSemantics = new FeatureAccumulator();
        FeatureAccumulator flinkSemantics = new FeatureAccumulator();
        for (TransactionEventV1 original : fixture()) {
            TransactionEventV1 transported = FlinkFeatureJob.parse(mapper.writeValueAsString(original));
            var light = lightweightSemantics.apply(original, ZoneOffset.UTC);
            var flink = flinkSemantics.apply(transported, ZoneOffset.UTC);
            assertEquals(light.values(), flink.values());
        }
    }

    private List<TransactionEventV1> fixture() {
        Instant first = Instant.parse("2026-07-22T01:00:00Z");
        Instant second = Instant.parse("2026-07-22T01:02:00Z");
        return List.of(event("e-1", "t-1", first, 100, "device-a", "counter-a"),
                event("e-2", "t-2", second, 200, "device-a", "counter-b"));
    }

    private TransactionEventV1 event(String eventId, String txnId, Instant time, long amount,
                                     String device, String counterparty) {
        return new TransactionEventV1(new EventMetadataV1(eventId, "c", "s", txnId, time, 1),
                "MOBILE", "TRANSFER", "account", counterparty, amount, "CNY", device,
                "127.0.0.1", time);
    }
}
