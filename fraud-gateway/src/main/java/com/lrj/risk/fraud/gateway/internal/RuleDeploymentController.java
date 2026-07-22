package com.lrj.risk.fraud.gateway.internal;

import java.util.List;

import com.lrj.risk.fraud.engine.HotReloadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal management adapter; production profile is protected by service JWT. */
@RestController
@RequestMapping("/internal/v1/rule-deployments")
public class RuleDeploymentController {

    private final HotReloadService hotReloadService;

    public RuleDeploymentController(HotReloadService hotReloadService) {
        this.hotReloadService = hotReloadService;
    }

    @PostMapping
    public ResponseEntity<Void> deploy(@Valid @RequestBody DeploymentRequest request) {
        hotReloadService.deploy(request.sourceId(), request.version(), request.ruleSets(), request.drl(),
                request.rolloutPercentage(), request.previousVersion(), request.previousRuleSets(),
                request.previousDrl(), request.shadowVersion(), request.shadowRuleSets(), request.shadowDrl());
        return ResponseEntity.noContent().build();
    }

    public record DeploymentRequest(
            @NotBlank String sourceId,
            @NotBlank String version,
            @NotEmpty List<@NotBlank String> ruleSets,
            @NotBlank String drl,
            @Min(0) @Max(100) int rolloutPercentage,
            String previousVersion,
            List<String> previousRuleSets,
            String previousDrl,
            String shadowVersion,
            List<String> shadowRuleSets,
            String shadowDrl) {

        public DeploymentRequest {
            boolean hasPrevious = previousVersion != null && !previousVersion.isBlank();
            if (rolloutPercentage < 100 && (!hasPrevious || previousRuleSets == null
                    || previousRuleSets.isEmpty() || previousDrl == null || previousDrl.isBlank())) {
                throw new IllegalArgumentException("partial rollout requires a complete previous release");
            }
            boolean hasShadow = shadowVersion != null && !shadowVersion.isBlank();
            if (hasShadow && (shadowRuleSets == null || shadowRuleSets.isEmpty()
                    || shadowDrl == null || shadowDrl.isBlank())) {
                throw new IllegalArgumentException("shadow deployment is incomplete");
            }
        }
    }
}
