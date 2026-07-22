package com.lrj.risk.admin.security;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("secure")
@EnableMethodSecurity
@EnableConfigurationProperties(RiskIdentityProperties.class)
public class CasdoorSecurityConfig {
    @Bean
    CasdoorAuthorityMapper casdoorAuthorityMapper() {
        return new CasdoorAuthorityMapper();
    }

    @Bean
    JwtDecoder jwtDecoder(RiskIdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> boundary = jwt -> validateBoundary(jwt, properties);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(standard, boundary));
        return decoder;
    }

    @Bean
    SecurityFilterChain secureSecurityFilterChain(HttpSecurity http, CasdoorAuthorityMapper authorityMapper)
            throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus", "/error").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/decisions/*/replay").hasAuthority("decision.replay")
                        .requestMatchers("/api/v1/decisions/**").hasAuthority("decision.read")
                        .requestMatchers("/api/v1/cases/*/claim", "/api/v1/cases/*/comments", "/api/v1/cases/*/resolve").hasAuthority("case.write")
                        .requestMatchers("/api/v1/cases/**").hasAuthority("case.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/profiles/tags/**").hasAuthority("profile.write")
                        .requestMatchers("/api/v1/profiles/**").hasAuthority("profile.read")
                        .requestMatchers("/api/v1/rules/releases/*/approve").hasAuthority("rule.approve")
                        .requestMatchers("/api/v1/rules/releases/*/publish", "/api/v1/rules/releases/*/rollback").hasAuthority("rule.publish")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rules/releases", "/api/v1/rules/releases/*/submit").hasAuthority("rule.write")
                        .requestMatchers("/api/v1/rules/**").hasAuthority("rule.read")
                        .requestMatchers("/api/v1/models/*/approve").hasAuthority("model.approve")
                        .requestMatchers("/api/v1/models/*/activate", "/api/v1/models/*/rollback").hasAuthority("model.activate")
                        .requestMatchers(HttpMethod.POST, "/api/v1/models/*/monitoring").hasAuthority("model.write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/models").hasAuthority("model.write")
                        .requestMatchers("/api/v1/models/**").hasAuthority("model.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/ratings/jobs/*/retry", "/api/v1/ratings/jobs").hasAuthority("rating.write")
                        .requestMatchers("/api/v1/ratings/**").hasAuthority("rating.read")
                        .requestMatchers("/api/v1/operations/dead-events/*/replay").hasAuthority("ops.replay")
                        .requestMatchers("/api/v1/operations/**").hasAuthority("ops.read")
                        .requestMatchers("/api/v1/audit/**").hasAuthority("audit.read")
                        .anyRequest().authenticated())
                .csrf(configurer -> configurer.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resource -> resource.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(authorityMapper.jwtAuthenticationConverter())))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) ->
                                SecurityErrorWriter.write(response, 401, "UNAUTHENTICATED", "需要登录"))
                        .accessDeniedHandler((request, response, failure) ->
                                SecurityErrorWriter.write(response, 403, "FORBIDDEN", "权限不足")))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny()))
                .build();
    }

    static OAuth2TokenValidatorResult validateBoundary(Jwt jwt, RiskIdentityProperties properties) {
        List<String> audiences = properties.getAudiences();
        boolean audienceAllowed = audiences != null && !audiences.isEmpty()
                && jwt.getAudience().stream().anyMatch(audiences::contains);
        if (!audienceAllowed) return invalid("access token audience 不属于 risk-platform");
        if (!properties.getOrganization().equals(jwt.getClaimAsString("owner"))) {
            return invalid("access token owner 不是 risk-platform");
        }
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) return invalid("access token 缺少 sub");
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult invalid(String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
    }
}
