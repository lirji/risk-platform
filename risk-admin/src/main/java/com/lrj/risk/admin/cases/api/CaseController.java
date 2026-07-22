package com.lrj.risk.admin.cases.api;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.cases.application.CaseWorkflowService;
import com.lrj.risk.admin.cases.application.CaseQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseQuery caseQuery;
    private final CaseWorkflowService workflow;

    public CaseController(CaseQuery caseQuery, CaseWorkflowService workflow) {
        this.caseQuery = caseQuery;
        this.workflow = workflow;
    }

    @GetMapping
    List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        return caseQuery.findByStatus(status, 200);
    }

    @PostMapping("/{caseId}/claim")
    void claim(@PathVariable String caseId,
               Authentication authentication) {
        workflow.claim(CurrentActor.tenant(authentication, "risk-platform"), caseId,
                CurrentActor.id(authentication, "local-analyst"));
    }

    @PostMapping("/{caseId}/comments")
    void comment(@PathVariable String caseId, @Valid @RequestBody CommentRequest request,
                 Authentication authentication) {
        workflow.comment(CurrentActor.tenant(authentication, "risk-platform"), caseId,
                CurrentActor.id(authentication, "local-analyst"), request.content());
    }

    @PostMapping("/{caseId}/resolve")
    void resolve(@PathVariable String caseId, @Valid @RequestBody ResolveRequest request,
                 Authentication authentication) {
        workflow.resolve(CurrentActor.tenant(authentication, "risk-platform"), caseId,
                CurrentActor.id(authentication, "local-analyst"), request.label(), request.reason());
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String content) { }
    public record ResolveRequest(@NotBlank String label, @NotBlank @Size(max = 1000) String reason) { }
}
