package com.lrj.risk.admin.ratings.api;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.ratings.application.RatingJobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.Authentication;
import com.lrj.risk.admin.security.CurrentActor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ratings/jobs")
public class RatingJobController {

    private final RatingJobService jobs;

    public RatingJobController(RatingJobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping List<Map<String, Object>> list() {
        return jobs.list();
    }

    @PostMapping Map<String, String> create(@Valid @RequestBody CreateRequest request,
            Authentication authentication) {
        String actor = CurrentActor.id(authentication, "local-rating-admin");
        String id = jobs.create(request.modelCode(), request.sourceIndex(), request.targetIndex(), actor);
        return Map.of("jobId", id);
    }

    @PostMapping("/{id}/retry") void retry(@PathVariable String id,
            Authentication authentication) {
        String actor = CurrentActor.id(authentication, "local-rating-admin");
        jobs.retry(id, actor);
    }

    public record CreateRequest(@NotBlank String modelCode, @NotBlank String sourceIndex,
                                @NotBlank String targetIndex) { }
}
