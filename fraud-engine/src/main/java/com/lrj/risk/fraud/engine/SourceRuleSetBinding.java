package com.lrj.risk.fraud.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.lrj.risk.fraud.application.port.out.RuleSetBindingPort;

import org.springframework.stereotype.Component;

/**
 * 来源 ↔ 规则集 绑定 (PLAN §3.5)。
 *
 * <p>交易带 sourceId 进来, 据此解析出要激活的规则集 (Drools agenda-group) 列表,
 * 引擎只激活这些规则集 —— 规则"写一次、按来源绑定生效", 不在规则里硬编码渠道条件。
 *
 * <p>管理面将已审核发布版本推送到此运行时适配器。绑定按来源隔离，并用 sourceId+txnId
 * 做确定性灰度分桶；同一交易重试始终命中同一版本。规则集名即 DRL 的 agenda-group 名。
 */
@Component
public class SourceRuleSetBinding implements RuleSetBindingPort {

    private static final List<String> BASELINE_RULESETS = List.of("blacklist", "threshold", "model");

    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();

    public SourceRuleSetBinding() {
        bindings.put("MOBILE_TRANSFER", new Binding(
                new RuleBinding(BASELINE_RULESETS, "baseline-1"), null, 100, null));
    }

    /** 解析某来源绑定的规则集 (agenda-group) 列表; 未配置则返回默认。 */
    @Override
    public RuleBinding resolve(String sourceId, String transactionId) {
        Binding binding = bindings.get(sourceId);
        if (binding == null) {
            throw new IllegalArgumentException("no published rule binding for sourceId=" + sourceId);
        }
        if (binding.previous() != null && bucket(sourceId, transactionId) >= binding.rolloutPercentage()) {
            return binding.previous();
        }
        return binding.active();
    }

    /** 绑定/更新某来源的规则集 (生产由管理后台调用)。 */
    public void bind(String sourceId, List<String> ruleSets) {
        bind(sourceId, ruleSets, "runtime", 100, null, null, null);
    }

    public void bind(String sourceId, List<String> ruleSets, String version) {
        bind(sourceId, ruleSets, version, 100, null, null, null);
    }

    public void bind(String sourceId, List<String> ruleSets, String version, int rolloutPercentage,
                     String previousVersion, List<String> previousRuleSets,
                     RuleBinding shadow) {
        if (sourceId == null || sourceId.isBlank() || ruleSets == null || ruleSets.isEmpty()) {
            throw new IllegalArgumentException("sourceId and ruleSets are required");
        }
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be 0..100");
        }
        RuleBinding previous = previousVersion == null ? null
                : new RuleBinding(Objects.requireNonNull(previousRuleSets), previousVersion);
        if (rolloutPercentage < 100 && previous == null) {
            throw new IllegalArgumentException("partial rollout requires a previous release");
        }
        bindings.put(sourceId, new Binding(new RuleBinding(ruleSets, version), previous,
                rolloutPercentage, shadow));
    }

    @Override
    public Optional<RuleBinding> shadow(String sourceId) {
        Binding binding = bindings.get(sourceId);
        return Optional.ofNullable(binding == null ? null : binding.shadow());
    }

    private int bucket(String sourceId, String transactionId) {
        return Math.floorMod(Objects.hash(sourceId, transactionId), 100);
    }

    private record Binding(RuleBinding active, RuleBinding previous, int rolloutPercentage,
                           RuleBinding shadow) {
    }
}
