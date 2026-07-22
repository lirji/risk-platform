package com.lrj.risk.admin.models.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PopulationStabilityIndexTest {
    @Test
    void identicalDistributionHasZeroAndShiftIsPositive() {
        assertEquals(0, PopulationStabilityIndex.calculate(new long[]{10, 20}, new long[]{10, 20}), 1e-12);
        assertTrue(PopulationStabilityIndex.calculate(new long[]{90, 10}, new long[]{10, 90}) > 1);
    }
}
