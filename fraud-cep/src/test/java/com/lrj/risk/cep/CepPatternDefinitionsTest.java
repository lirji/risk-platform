package com.lrj.risk.cep;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class CepPatternDefinitionsTest {

    @Test
    void bothRequiredPatternsAreDefined() {
        assertNotNull(CepPatternDefinitions.probeThenLarge());
        assertNotNull(CepPatternDefinitions.failedThenSuccess());
    }
}
