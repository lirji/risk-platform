package com.lrj.risk.fraud.gateway;

import com.lrj.risk.fraud.engine.HotReloadService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 规则管理: 运行时热发布 DRL (不重启)。
 *
 * <p>请求体为一段 DRL (须声明 package rules.fraud + import 所需类); 编译成功则切换生效,
 * 失败返回 400 + Drools 报错(含行列号)。生产应换 KieScanner + 审核流 (PLAN §3.5)。
 */
@RestController
@RequestMapping("/rules")
public class RuleAdminController {

    private final HotReloadService hotReloadService;

    public RuleAdminController(HotReloadService hotReloadService) {
        this.hotReloadService = hotReloadService;
    }

    @PostMapping(value = "/reload", consumes = "text/plain")
    public ResponseEntity<String> reload(@RequestBody String drl) {
        try {
            hotReloadService.reload(drl);
            return ResponseEntity.ok("规则已热发布生效");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body("规则编译失败:\n" + e.getMessage());
        }
    }
}
