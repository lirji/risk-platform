package com.lrj.risk.admin.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "CASDOOR_CLIENT_ID=test-console",
        "CASDOOR_CLIENT_SECRET=test-secret",
        "CASDOOR_SERVICE_CLIENT_ID=test-service",
        "CASDOOR_SERVICE_CLIENT_SECRET=test-service-secret",
        "spring.security.oauth2.client.provider.casdoor.authorization-uri=https://casdoor.invalid/login/oauth/authorize",
        "spring.security.oauth2.client.provider.casdoor.token-uri=https://casdoor.invalid/api/login/oauth/access_token",
        "spring.security.oauth2.client.provider.casdoor.jwk-set-uri=https://casdoor.invalid/.well-known/jwks",
        "spring.security.oauth2.client.provider.casdoor.user-info-uri=https://casdoor.invalid/api/userinfo",
        "spring.security.oauth2.client.provider.casdoor.user-name-attribute=sub",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://casdoor.invalid/.well-known/jwks"
})
@ActiveProfiles("secure")
@AutoConfigureMockMvc
class SecurityRouteMatrixTest {
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean ClientRegistrationRepository clientRegistrationRepository;
    @MockBean OAuth2AuthorizedClientService authorizedClientService;

    @Test
    void anonymousIsRejectedAndReadPermissionIsSufficientForReadOnlyRoute() throws Exception {
        mvc.perform(get("/api/v1/decisions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/decisions").with(jwt()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/decisions").with(jwt()
                        .authorities(new SimpleGrantedAuthority("decision.read"))))
                .andExpect(status().isOk());
    }

    @Test
    void readPermissionCannotInvokeReplay() throws Exception {
        mvc.perform(post("/api/v1/decisions/not-present/replay")
                        .with(jwt().authorities(new SimpleGrantedAuthority("decision.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthRemainsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }
}
