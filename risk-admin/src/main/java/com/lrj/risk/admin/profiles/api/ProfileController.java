package com.lrj.risk.admin.profiles.api;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.profiles.application.ProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/{accountNo}")
    Map<String, Object> profile(@PathVariable String accountNo) {
        return profiles.profile(accountNo);
    }

    @GetMapping("/tags")
    List<Map<String, Object>> tags() {
        return profiles.tags();
    }

    @PostMapping("/tags")
    void createTag(@Valid @RequestBody TagRequest request,
                   Authentication authentication) {
        String actor = CurrentActor.id(authentication, "local-profile-admin");
        profiles.createTag(request.code(), request.name(), request.valueType(), request.definition(),
                request.freshnessSeconds(), actor);
    }

    @PostMapping("/tags/{code}/activate")
    void activate(@PathVariable String code) {
        profiles.activate(code);
    }

    @PostMapping("/tags/{code}/deprecate")
    void deprecate(@PathVariable String code) {
        profiles.deprecate(code);
    }

    public record TagRequest(@NotBlank String code, @NotBlank String name, @NotBlank String valueType,
                             @NotBlank String definition, @Min(1) long freshnessSeconds) { }
}
