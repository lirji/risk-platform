package com.lrj.risk.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RiskTokenBoundaryTest {
    private final RiskIdentityProperties properties = new RiskIdentityProperties();

    @Test
    void acceptsOnlyConfiguredAudienceOrganizationAndSubject() {
        assertThat(CasdoorSecurityConfig.validateBoundary(jwt("risk-platform",
                "ragshared0client00000001-org-risk-platform", "user-1"), properties).hasErrors()).isFalse();
        assertThat(CasdoorSecurityConfig.validateBoundary(jwt("other",
                "ragshared0client00000001-org-risk-platform", "user-1"), properties).hasErrors()).isTrue();
        assertThat(CasdoorSecurityConfig.validateBoundary(jwt("risk-platform",
                "another-client", "user-1"), properties).hasErrors()).isTrue();
        assertThat(CasdoorSecurityConfig.validateBoundary(jwt("risk-platform",
                "ragshared0client00000001-org-risk-platform", ""), properties).hasErrors()).isTrue();
    }

    private Jwt jwt(String owner, String audience, String subject) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"),
                Map.of("sub", subject, "owner", owner, "aud", List.of(audience)));
    }
}
