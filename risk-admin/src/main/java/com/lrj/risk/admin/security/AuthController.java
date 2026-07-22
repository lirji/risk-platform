package com.lrj.risk.admin.security;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("secure")
public class AuthController {
    @GetMapping("/me")
    Map<String, Object> me(Authentication authentication) {
        Set<String> permissions = new LinkedHashSet<>();
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value.startsWith("ROLE_")) roles.add(value.substring(5).toLowerCase().replace('_', '-'));
            else if (!value.startsWith("SCOPE_") && !value.equals("OIDC_USER") && !value.equals("OAUTH2_USER")) {
                permissions.add(value);
            }
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Map.of("authenticated", true, "id", jwt.getSubject(), "displayName", displayName(jwt),
                "tenant", jwt.getClaimAsString("owner"), "roles", roles, "permissions", permissions,
                "mode", "auth-platform");
    }

    private String displayName(Jwt jwt) {
        for (String claim : new String[] {"displayName", "name", "preferred_username"}) {
            Object value = jwt.getClaims().get(claim);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return jwt.getSubject();
    }
}
