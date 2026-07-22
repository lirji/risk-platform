package com.lrj.risk.decisionlog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class DecisionLogTokenBoundaryTest {
    private DecisionLogIdentityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DecisionLogIdentityProperties();
    }

    @Test
    void acceptsOnlyTheRiskHumanClientAndTenant() {
        assertThat(validate("ragshared0client00000001-org-risk-platform", "risk-platform", "user-1")).isTrue();
        assertThat(validate("foreign-client", "risk-platform", "user-1")).isFalse();
        assertThat(validate("ragshared0client00000001-org-risk-platform", "other-org", "user-1")).isFalse();
        assertThat(validate("ragshared0client00000001-org-risk-platform", "risk-platform", "")).isFalse();
    }

    private boolean validate(String audience, String owner, String subject) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"), Map.of(
                "iss", properties.getIssuer(), "aud", List.of(audience), "owner", owner, "sub", subject));
        return !DecisionLogSecurityConfig.validateBoundary(jwt, properties).hasErrors();
    }
}
