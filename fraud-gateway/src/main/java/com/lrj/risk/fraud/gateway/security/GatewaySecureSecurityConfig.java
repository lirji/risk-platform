package com.lrj.risk.fraud.gateway.security;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
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
@Profile("secure")
@EnableConfigurationProperties(GatewayIdentityProperties.class)
public class GatewaySecureSecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(GatewayIdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> boundary = jwt -> validateBoundary(jwt, properties);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(standard, boundary));
        return decoder;
    }

    @Bean
    SecurityFilterChain gatewaySecureSecurityFilterChain(HttpSecurity http,
                                                         GatewayIdentityProperties properties) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new ClaimAuthoritiesConverter(properties));
        converter.setPrincipalClaimName("sub");
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus", "/error").permitAll()
                        .requestMatchers("/api/v1/risk/**").hasAuthority("risk.evaluate")
                        .requestMatchers("/internal/**").hasAuthority("service.runtime.deploy")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) ->
                                write(response, 401, "UNAUTHENTICATED", "需要有效的访问令牌"))
                        .accessDeniedHandler((request, response, failure) ->
                                write(response, 403, "FORBIDDEN", "权限不足")))
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))
                .build();
    }

    static OAuth2TokenValidatorResult validateBoundary(Jwt jwt, GatewayIdentityProperties properties) {
        boolean audienceAllowed = jwt.getAudience().stream().anyMatch(properties.allAudiences()::contains);
        if (!audienceAllowed) return invalid("access token audience 不属于 fraud-gateway");
        if (!properties.getOrganization().equals(jwt.getClaimAsString("owner"))) {
            return invalid("access token owner 不是 risk-platform");
        }
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) return invalid("access token 缺少 sub");
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult invalid(String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
    }

    private static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().printf("{\"code\":\"%s\",\"message\":\"%s\"}", code, message);
    }

    static final class ClaimAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        private final GatewayIdentityProperties properties;

        ClaimAuthoritiesConverter(GatewayIdentityProperties properties) {
            this.properties = properties;
        }

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            addPermissions(authorities, jwt.getClaim("permissions"));
            if (jwt.getAudience().stream().anyMatch(properties.getEvaluationAudiences()::contains)) {
                authorities.add(new SimpleGrantedAuthority("risk.evaluate"));
            }
            if (jwt.getAudience().stream().anyMatch(properties.getRuntimeAudiences()::contains)) {
                authorities.add(new SimpleGrantedAuthority("service.runtime.deploy"));
            }
            return authorities;
        }

        private void addPermissions(Set<GrantedAuthority> target, Object claim) {
            if (claim instanceof Collection<?> values) {
                values.forEach(value -> {
                    Object name = value instanceof java.util.Map<?, ?> permission ? permission.get("name") : value;
                    if (name != null && !String.valueOf(name).isBlank()) {
                        target.add(new SimpleGrantedAuthority(String.valueOf(name)));
                    }
                });
            } else if (claim instanceof String value) {
                for (String item : value.split("[ ,]+")) {
                    if (!item.isBlank()) target.add(new SimpleGrantedAuthority(item));
                }
            }
        }
    }
}
