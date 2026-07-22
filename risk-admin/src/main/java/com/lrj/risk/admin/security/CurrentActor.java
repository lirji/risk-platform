package com.lrj.risk.admin.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentActor {
    private CurrentActor() { }

    public static String id(Authentication authentication, String developmentFallback) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return developmentFallback;
        }
        return authentication.getName();
    }

    public static String tenant(Authentication authentication, String developmentFallback) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String owner = jwt.getClaimAsString("owner");
            if (owner != null && !owner.isBlank()) return owner;
        }
        return developmentFallback;
    }
}
