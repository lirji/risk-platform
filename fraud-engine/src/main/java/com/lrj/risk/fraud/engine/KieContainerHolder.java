package com.lrj.risk.fraud.engine;

import org.kie.api.runtime.KieContainer;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持有当前生效的 Drools 容器, 支持热替换 (规则热发布)。
 *
 * <p>FraudEngineService 每次取 {@link #get()} 拿最新容器; 热发布编译出新容器后 {@link #replace} 原子切换。
 * 进行中的旧 KieSession 关联创建时的容器引用, 不受切换影响 (drools-demo Step 9 验证的热加载安全性)。
 */
@Component
public class KieContainerHolder {

    private volatile KieContainer baseline = DroolsConfig.buildFraudKieContainer();
    private final Map<String, KieContainer> deployments = new ConcurrentHashMap<>();

    public KieContainer get() {
        return baseline;
    }

    public KieContainer get(String sourceId, String version) {
        if ("baseline-1".equals(version)) return baseline;
        KieContainer container = deployments.get(key(sourceId, version));
        if (container == null) {
            throw new IllegalStateException("rule runtime is not deployed for sourceId=" + sourceId
                    + ", version=" + version);
        }
        return container;
    }

    public void replace(KieContainer newContainer) {
        this.baseline = newContainer;
    }

    public void install(String sourceId, String version, KieContainer container) {
        deployments.put(key(sourceId, version), container);
    }

    private String key(String sourceId, String version) {
        return sourceId + "\u0000" + version;
    }
}
