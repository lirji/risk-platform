package com.lrj.risk.fraud.engine;

import org.kie.api.runtime.KieContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 规则热发布 (drools-demo Step 9 风格): 运行时编译新增 DRL → 重建容器 → 不重启切换。
 *
 * <p>编译错误时 {@link DroolsConfig#build} 抛 IllegalStateException(含行列号), 由控制层转 400。
 * 生产正解是 KieScanner + KJAR (规则随代码独立发版, drools-demo Step 16); 本实现适合小步热修。
 */
@Service
public class HotReloadService {

    private static final Logger log = LoggerFactory.getLogger(HotReloadService.class);

    private final KieContainerHolder holder;
    private final SourceRuleSetBinding bindings;

    public HotReloadService(KieContainerHolder holder, SourceRuleSetBinding bindings) {
        this.holder = holder;
        this.bindings = bindings;
    }

    /** 用 classpath 基线规则 + 传入的额外 DRL 重建并切换容器; 编译失败抛 IllegalStateException。 */
    public void reload(String extraDrl) {
        KieContainer rebuilt = DroolsConfig.build(extraDrl);  // 编译失败在此抛出
        holder.replace(rebuilt);
        log.info("规则热发布成功, 容器已切换");
    }

    public synchronized void deploy(String sourceId, String version, java.util.List<String> ruleSets,
                                    String extraDrl, int rolloutPercentage,
                                    String previousVersion, java.util.List<String> previousRuleSets,
                                    String previousDrl, String shadowVersion,
                                    java.util.List<String> shadowRuleSets, String shadowDrl) {
        KieContainer active = DroolsConfig.build(extraDrl);
        KieContainer previous = previousVersion == null ? null : DroolsConfig.build(previousDrl);
        KieContainer shadow = shadowVersion == null ? null : DroolsConfig.build(shadowDrl);

        holder.install(sourceId, version, active);
        if (previous != null) holder.install(sourceId, previousVersion, previous);
        if (shadow != null) holder.install(sourceId, shadowVersion, shadow);
        bindings.bind(sourceId, ruleSets, version, rolloutPercentage,
                previousVersion, previousRuleSets,
                shadowVersion == null ? null
                        : new com.lrj.risk.fraud.application.port.out.RuleSetBindingPort.RuleBinding(
                                shadowRuleSets, shadowVersion));
        log.info("rule release activated sourceId={} version={} rollout={} shadowVersion={}",
                sourceId, version, rolloutPercentage, shadowVersion);
    }
}
