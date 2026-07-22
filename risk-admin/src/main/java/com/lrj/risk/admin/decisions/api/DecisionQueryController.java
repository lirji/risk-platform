package com.lrj.risk.admin.decisions.api;

import java.util.Map;

import com.lrj.risk.admin.decisions.application.DecisionQuery;
import com.lrj.risk.admin.decisions.application.DecisionReplayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.Authentication;
import com.lrj.risk.admin.security.CurrentActor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionQueryController {

    private final DecisionQuery decisionQuery;
    private final DecisionReplayService replayService;

    public DecisionQueryController(DecisionQuery decisionQuery, DecisionReplayService replayService) {
        this.decisionQuery = decisionQuery;
        this.replayService = replayService;
    }

    @GetMapping
    DecisionQuery.Page<DecisionQuery.DecisionView> search(@RequestParam(required = false) String riskLevel,
                                                           @RequestParam(required = false, name = "q") String transactionId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return decisionQuery.search(riskLevel, transactionId, page, size);
    }

    @GetMapping("/{decisionId}")
    Map<String, Object> detail(@PathVariable String decisionId) {
        return decisionQuery.detail(decisionId);
    }

    @PostMapping("/{decisionId}/replay")
    Map<String, String> replay(@PathVariable String decisionId, @Valid @RequestBody ReplayRequest request,
            Authentication authentication) {
        return Map.of("eventId", replayService.replay(decisionId,
                CurrentActor.id(authentication, "local-admin"), request.reason()));
    }

    public record ReplayRequest(@NotBlank String reason) { }
}
