package com.lrj.risk.admin.rules.api;

import java.util.List;

import com.lrj.risk.admin.rules.application.RuleReleaseService;
import com.lrj.risk.admin.rules.domain.RuleRelease;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.Authentication;
import com.lrj.risk.admin.security.CurrentActor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules/releases")
public class RuleReleaseController {

    private final RuleReleaseService service;

    public RuleReleaseController(RuleReleaseService service) {
        this.service = service;
    }

    @GetMapping
    List<RuleRelease> list() { return service.list(); }

    @PostMapping
    RuleRelease create(@Valid @RequestBody CreateRequest request,
                       Authentication authentication) {
        return service.create(request.ruleCode(), request.ruleName(), request.drl(),
                CurrentActor.id(authentication, "local-admin"));
    }

    @PostMapping("/{id}/submit")
    RuleRelease submit(@PathVariable String id,
                       Authentication authentication) {
        return service.submit(id, CurrentActor.id(authentication, "local-admin"));
    }

    @PostMapping("/{id}/approve")
    RuleRelease approve(@PathVariable String id,
                        Authentication authentication) {
        return service.approve(id, CurrentActor.id(authentication, "local-reviewer"));
    }

    @PostMapping("/{id}/publish")
    RuleRelease publish(@PathVariable String id, @Valid @RequestBody PublishRequest request,
                        Authentication authentication) {
        return service.publish(id, request.sourceId(), request.ruleSets(), request.rolloutPercentage(),
                request.shadowReleaseId(), CurrentActor.id(authentication, "local-admin"));
    }

    @PostMapping("/{id}/rollback")
    RuleRelease rollback(@PathVariable String id, @Valid @RequestBody RollbackRequest request,
                         Authentication authentication) {
        return service.rollback(id, request.sourceId(), CurrentActor.id(authentication, "local-admin"));
    }

    public record CreateRequest(@NotBlank String ruleCode, @NotBlank String ruleName,
                                @NotBlank String drl) { }

    public record PublishRequest(@NotBlank String sourceId, @NotEmpty List<@NotBlank String> ruleSets,
                                 @Min(0) @Max(100) int rolloutPercentage, String shadowReleaseId) { }
    public record RollbackRequest(@NotBlank String sourceId) { }
}
