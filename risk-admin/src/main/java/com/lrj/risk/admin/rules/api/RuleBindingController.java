package com.lrj.risk.admin.rules.api;

import java.util.List;

import com.lrj.risk.admin.rules.application.RuleReleaseService;
import com.lrj.risk.admin.rules.application.port.RuleReleaseRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules/bindings")
public class RuleBindingController {
    private final RuleReleaseService rules;

    public RuleBindingController(RuleReleaseService rules) {
        this.rules = rules;
    }

    @GetMapping
    List<RuleReleaseRepository.Binding> list() {
        return rules.bindings();
    }
}
