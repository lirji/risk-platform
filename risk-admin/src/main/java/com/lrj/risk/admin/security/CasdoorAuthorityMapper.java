package com.lrj.risk.admin.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.Jwt;

/** 将统一 Casdoor 的 permissions/roles/groups claim 映射为本地 API authority。 */
public final class CasdoorAuthorityMapper {
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::fromJwt);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    private Collection<GrantedAuthority> fromJwt(Jwt jwt) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>();
        addClaims(mapped, jwt.getClaims());
        return mapped;
    }

    private void addClaims(Set<GrantedAuthority> mapped, Map<String, Object> claims) {
        addPermissions(mapped, claims.get("permissions"));
        addValues(mapped, claims.get("roles"), true);
        addValues(mapped, claims.get("groups"), true);
        addScope(mapped, claims.get("scope"));
        addScope(mapped, claims.get("scp"));
    }

    private void addPermissions(Set<GrantedAuthority> mapped, Object claim) {
        if (claim instanceof Collection<?> values) {
            values.forEach(value -> {
                if (value instanceof Map<?, ?> permission) {
                    addPermission(mapped, permission.get("name"));
                } else {
                    addPermission(mapped, value);
                }
            });
        } else {
            addPermission(mapped, claim);
        }
    }

    private void addPermission(Set<GrantedAuthority> mapped, Object value) {
        if (value == null) return;
        String permission = String.valueOf(value).trim();
        if (!permission.isEmpty()) mapped.add(new SimpleGrantedAuthority(permission));
    }

    private void addValues(Set<GrantedAuthority> mapped, Object claim, boolean role) {
        if (claim instanceof Collection<?> values) {
            values.forEach(value -> {
                Object name = value instanceof Map<?, ?> item ? item.get("name") : value;
                if (name != null) addValue(mapped, String.valueOf(name), role);
            });
        } else if (claim instanceof Map<?, ?> item) {
            Object name = item.get("name");
            if (name != null) addValue(mapped, String.valueOf(name), role);
        } else if (claim instanceof String value) {
            for (String item : value.split("[ ,]+")) addValue(mapped, item, role);
        }
    }

    private void addValue(Set<GrantedAuthority> mapped, String value, boolean role) {
        String normalized = value.trim();
        if (normalized.isEmpty()) return;
        if (!role) {
            mapped.add(new SimpleGrantedAuthority(normalized));
            return;
        }
        int slash = normalized.lastIndexOf('/');
        String shortName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        mapped.add(new SimpleGrantedAuthority("ROLE_" + shortName.toUpperCase().replace('-', '_')));
    }

    private void addScope(Set<GrantedAuthority> mapped, Object claim) {
        if (claim instanceof Collection<?> values) {
            values.forEach(value -> mapped.add(new SimpleGrantedAuthority("SCOPE_" + value)));
        } else if (claim instanceof String value) {
            for (String scope : value.split(" ")) {
                if (!scope.isBlank()) mapped.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
        }
    }
}
