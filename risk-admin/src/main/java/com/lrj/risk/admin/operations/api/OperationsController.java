package com.lrj.risk.admin.operations.api;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.operations.application.OperationsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.Authentication;
import com.lrj.risk.admin.security.CurrentActor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {

    private final OperationsService operations;

    public OperationsController(OperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/dead-events")
    List<Map<String, Object>> deadEvents() {
        return operations.deadEvents();
    }

    @PostMapping("/dead-events/{eventId}/replay")
    void replay(@PathVariable String eventId,
                Authentication authentication) {
        operations.replay(eventId, CurrentActor.id(authentication, "local-admin"));
    }
}
