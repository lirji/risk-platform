package com.lrj.risk.fraud.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.lrj.risk.fraud.application.port.out.RuleSetBindingPort.RuleBinding;
import org.junit.jupiter.api.Test;

class SourceRuleSetBindingTest {

    @Test
    void rolloutIsDeterministicAndCanKeepAllTrafficOnPreviousVersion() {
        var bindings = new SourceRuleSetBinding();
        bindings.bind("BANK_A", List.of("new"), "rules-2", 0,
                "rules-1", List.of("old"), null);

        assertEquals("rules-1", bindings.resolve("BANK_A", "txn-1").version());
        assertEquals("rules-1", bindings.resolve("BANK_A", "txn-1").version());

        bindings.bind("BANK_A", List.of("new"), "rules-2", 100,
                "rules-1", List.of("old"), new RuleBinding(List.of("shadow"), "rules-3"));
        assertEquals("rules-2", bindings.resolve("BANK_A", "txn-1").version());
        assertEquals("rules-3", bindings.shadow("BANK_A").orElseThrow().version());
    }

    @Test
    void partialRolloutRequiresPreviousReleaseAndUnknownSourceFailsSafe() {
        var bindings = new SourceRuleSetBinding();
        assertThrows(IllegalArgumentException.class, () -> bindings.bind(
                "BANK_A", List.of("new"), "rules-2", 10, null, null, null));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> bindings.resolve("UNKNOWN", "txn-1"));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("no published rule binding"));
    }
}
