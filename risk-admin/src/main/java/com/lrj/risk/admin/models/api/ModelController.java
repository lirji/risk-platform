package com.lrj.risk.admin.models.api;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.models.application.ModelRegistryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
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
@RequestMapping("/api/v1/models")
public class ModelController {

    private final ModelRegistryService service;

    public ModelController(ModelRegistryService service) { this.service = service; }

    @GetMapping List<Map<String, Object>> list() { return service.list(); }

    @PostMapping
    Map<String, String> register(@Valid @RequestBody RegisterRequest request,
                                 Authentication authentication) {
        return Map.of("modelId", service.register(request.modelCode(), request.version(), request.artifactUri(),
                request.checksum(), request.metrics(), request.trainingDataVersion(),
                CurrentActor.id(authentication, "local-model-admin")));
    }

    @PostMapping("/{id}/approve") void approve(@PathVariable String id,
            Authentication authentication) {
        service.approve(id, CurrentActor.id(authentication, "local-model-reviewer"));
    }

    @PostMapping("/{id}/activate") void activate(@PathVariable String id,
            @Valid @RequestBody(required = false) ActivationRequest request,
            Authentication authentication) {
        service.activate(id, CurrentActor.id(authentication, "local-model-admin"),
                request == null ? 100 : request.rolloutPercentage());
    }

    @PostMapping("/{id}/rollback") void rollback(@PathVariable String id, Authentication authentication) {
        service.rollback(id, CurrentActor.id(authentication, "local-model-admin"));
    }

    public record RegisterRequest(@NotBlank String modelCode, @Min(1) int version,
                                  @NotBlank String artifactUri, @NotBlank String checksum,
                                  @NotEmpty Map<String, Double> metrics,
                                  @NotBlank String trainingDataVersion) { }
    public record ActivationRequest(@Min(0) @Max(100) int rolloutPercentage) { }
}
