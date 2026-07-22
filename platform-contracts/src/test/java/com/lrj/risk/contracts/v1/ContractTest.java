package com.lrj.risk.contracts.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lrj.risk.contracts.kernel.TransactionKey;
import org.junit.jupiter.api.Test;

class ContractTest {

    @Test
    void rejectsInvalidEventVersionAndAmount() {
        Instant now = Instant.parse("2026-07-22T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new EventMetadataV1("e", "c", "s", "t", now, 2));
        EventMetadataV1 metadata = new EventMetadataV1("e", "c", "s", "t", now, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionEventV1(metadata, "APP", "TRANSFER", "a", null,
                        0, "CNY", null, null, now));
    }

    @Test
    void decisionDefensivelyCopiesCollections() {
        EventMetadataV1 metadata = EventMetadataV1.create("correlation", "bank-a", "txn-1", Instant.now());
        List<RuleHitV1> hits = new ArrayList<>();
        Map<String, String> features = new HashMap<>();
        DecisionEventV1 event = new DecisionEventV1(metadata, "decision-1", DecisionRiskLevel.LOW,
                DecisionAction.ALLOW, 0.1, hits, features, "rules-1", "model-1", 2);
        hits.add(new RuleHitV1("R1", "rule", 1, "reason"));
        features.put("daily_count", "9");
        assertEquals(List.of(), event.hitRules());
        assertEquals(Map.of(), event.featureSnapshot());
    }

    @Test
    void transactionKeyIsCanonical() {
        assertEquals("bank-a:txn-1", new TransactionKey("bank-a", "txn-1").value());
    }
}
