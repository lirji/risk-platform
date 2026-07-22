package com.lrj.risk.fraud.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class GatewayClaimAuthoritiesConverterTest {
    @Test
    void bindsMachineCapabilityToAudienceAndParsesPermissionObjects() {
        GatewayIdentityProperties properties = new GatewayIdentityProperties();
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", "service", "owner", "risk-platform",
                "aud", List.of("risk-bank-client"), "permissions", List.of(Map.of("name", "observed.permission")),
                "scope", "service.runtime.deploy"));

        assertThat(new GatewaySecureSecurityConfig.ClaimAuthoritiesConverter(properties).convert(jwt))
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("observed.permission", "risk.evaluate")
                .doesNotContain("service.runtime.deploy", "SCOPE_service.runtime.deploy");
    }
}
