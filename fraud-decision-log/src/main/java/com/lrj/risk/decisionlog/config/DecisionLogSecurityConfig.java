package com.lrj.risk.decisionlog.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(DecisionLogIdentityProperties.class)
public class DecisionLogSecurityConfig {
    @Bean
    @Profile("dev")
    SecurityFilterChain decisionLogDevChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    @Bean
    @Profile("secure")
    JwtDecoder decisionLogJwtDecoder(DecisionLogIdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> boundary = jwt -> validateBoundary(jwt, properties);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(standard, boundary));
        return decoder;
    }

    @Bean
    @Profile("secure")
    SecurityFilterChain decisionLogSecureChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            Object claim = jwt.getClaim("permissions");
            if (claim instanceof Collection<?> values) {
                values.forEach(value -> {
                    Object name = value instanceof Map<?, ?> permission ? permission.get("name") : value;
                    if (name != null && !String.valueOf(name).isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(String.valueOf(name)));
                    }
                });
            } else if (claim instanceof String value) {
                for (String item : value.split("[ ,]+")) {
                    if (!item.isBlank()) authorities.add(new SimpleGrantedAuthority(item));
                }
            }
            return authorities;
        });
        converter.setPrincipalClaimName("sub");
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus", "/error").permitAll()
                        .requestMatchers("/api/v1/decision-log/**").hasAuthority("decision.read")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    static OAuth2TokenValidatorResult validateBoundary(Jwt jwt, DecisionLogIdentityProperties properties) {
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
