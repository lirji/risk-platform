package com.lrj.risk.fraud.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class GatewayTokenBoundaryTest {
    private final GatewayIdentityProperties properties = new GatewayIdentityProperties();

    @Test
    void acceptsOnlyRiskMachineAudiencesFromRiskOrganization() {
        assertThat(GatewaySecureSecurityConfig.validateBoundary(
                jwt("risk-platform", "risk-bank-client", "risk-platform/risk-bank-service"), properties)
                .hasErrors()).isFalse();
        assertThat(GatewaySecureSecurityConfig.validateBoundary(
                jwt("risk-platform", "risk-runtime-client", "risk-platform/risk-runtime-service"), properties)
                .hasErrors()).isFalse();
        assertThat(GatewaySecureSecurityConfig.validateBoundary(
                jwt("other", "risk-bank-client", "machine"), properties).hasErrors()).isTrue();
        assertThat(GatewaySecureSecurityConfig.validateBoundary(
                jwt("risk-platform", "foreign-client", "machine"), properties).hasErrors()).isTrue();
    }

    private Jwt jwt(String owner, String audience, String subject) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"),
                Map.of("sub", subject, "owner", owner, "aud", List.of(audience)));
    }
}
