package com.lrj.risk.admin.audit.api;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.audit.application.AuditQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQuery auditQuery;

    public AuditController(AuditQuery auditQuery) { this.auditQuery = auditQuery; }

    @GetMapping
    List<Map<String, Object>> list() {
        return auditQuery.latest(500);
    }
}
