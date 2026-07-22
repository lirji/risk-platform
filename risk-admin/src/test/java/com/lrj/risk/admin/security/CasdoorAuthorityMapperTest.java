package com.lrj.risk.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CasdoorAuthorityMapperTest {
    @Test
    void readsPermissionObjectsAndNeverExpandsRolesLocally() {
        CasdoorAuthorityMapper mapper = new CasdoorAuthorityMapper();
        Jwt token = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of(
                        "sub", "analyst-1",
                        "groups", List.of(Map.of("name", "risk-platform/risk-analysts")),
                        "roles", List.of(Map.of("name", "risk-analyst")),
                        "permissions", List.of(Map.of("name", "profile.read"), Map.of("name", "case.read"))));

        assertThat(mapper.jwtAuthenticationConverter().convert(token).getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("ROLE_RISK_ANALYST", "ROLE_RISK_ANALYSTS", "profile.read", "case.read")
                .doesNotContain("case.write", "decision.read");
    }
}
