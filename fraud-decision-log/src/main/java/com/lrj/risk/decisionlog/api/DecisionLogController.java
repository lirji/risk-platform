package com.lrj.risk.decisionlog.api;

import java.util.Map;

import com.lrj.risk.decisionlog.application.DecisionIndexPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decision-log")
public class DecisionLogController {
    private final DecisionIndexPort index;
    public DecisionLogController(DecisionIndexPort index) { this.index = index; }

    @GetMapping
    Map<String, Object> search(@RequestParam(required = false) String riskLevel,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return index.search(riskLevel, page, size);
    }

    @GetMapping("/{decisionId}")
    Map<String, Object> detail(@PathVariable String decisionId) { return index.detail(decisionId); }
}
