package com.lrj.risk.fraud.engine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 来源 ↔ 规则集 绑定 (PLAN §3.5)。
 *
 * <p>交易带 sourceId 进来, 据此解析出要激活的规则集 (Drools agenda-group) 列表,
 * 引擎只激活这些规则集 —— 规则"写一次、按来源绑定生效", 不在规则里硬编码渠道条件。
 *
 * <p>最小实现: 内存 Map + 默认绑定。生产版应由 fraud-rule-mgmt 存 MySQL (t_source_ruleset_bind) 并热加载。
 * 规则集名即 DRL 里的 agenda-group 名。
 */
@Component
public class SourceRuleSetBinding {

    /** 默认规则集: 黑名单 + 阈值 + 模型分, 所有来源至少都过这三组。 */
    private static final List<String> DEFAULT_RULESETS = List.of("blacklist", "threshold", "model");

    private final Map<String, List<String>> bindings = new ConcurrentHashMap<>();

    /** 解析某来源绑定的规则集 (agenda-group) 列表; 未配置则返回默认。 */
    public List<String> resolve(String sourceId) {
        if (sourceId == null) {
            return DEFAULT_RULESETS;
        }
        return bindings.getOrDefault(sourceId, DEFAULT_RULESETS);
    }

    /** 绑定/更新某来源的规则集 (生产由管理后台调用)。 */
    public void bind(String sourceId, List<String> ruleSets) {
        bindings.put(sourceId, List.copyOf(ruleSets));
    }
}
