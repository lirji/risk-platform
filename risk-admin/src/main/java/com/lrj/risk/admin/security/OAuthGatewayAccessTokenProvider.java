package com.lrj.risk.admin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
@Profile("secure")
public class OAuthGatewayAccessTokenProvider implements GatewayAccessTokenProvider {
    private final OAuth2AuthorizedClientManager manager;

    public OAuthGatewayAccessTokenProvider(OAuth2AuthorizedClientManager manager) {
        this.manager = manager;
    }

    @Override
    public String bearerToken() {
        var request = OAuth2AuthorizeRequest.withClientRegistrationId("risk-service")
                .principal("risk-admin-service")
                .build();
        var client = manager.authorize(request);
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("Casdoor did not issue a service access token");
        }
        return client.getAccessToken().getTokenValue();
    }

    @Bean
    static OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials().build());
        return manager;
    }
}
