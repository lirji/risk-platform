package com.lrj.risk.admin.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("dev")
public class DevAuthController {
    private static final Set<String> PERMISSIONS = new LinkedHashSet<>(Arrays.asList(
            "dashboard.read", "decision.read", "decision.replay", "case.read", "case.write",
            "profile.read", "profile.write", "rule.read", "rule.write", "rule.approve", "rule.publish",
            "model.read", "model.write", "model.approve", "model.activate", "rating.read", "rating.write",
            "ops.read", "ops.replay", "audit.read"));

    @GetMapping("/me")
    Map<String, Object> me() {
        return Map.of("authenticated", true, "id", "local-developer", "displayName", "本地开发者",
                "roles", Set.of("risk-admin"), "permissions", PERMISSIONS, "mode", "dev");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout() { }
}
