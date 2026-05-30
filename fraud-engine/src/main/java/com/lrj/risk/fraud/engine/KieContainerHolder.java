package com.lrj.risk.fraud.engine;

import org.kie.api.runtime.KieContainer;
import org.springframework.stereotype.Component;

/**
 * 持有当前生效的 Drools 容器, 支持热替换 (规则热发布)。
 *
 * <p>FraudEngineService 每次取 {@link #get()} 拿最新容器; 热发布编译出新容器后 {@link #replace} 原子切换。
 * 进行中的旧 KieSession 关联创建时的容器引用, 不受切换影响 (drools-demo Step 9 验证的热加载安全性)。
 */
@Component
public class KieContainerHolder {

    private volatile KieContainer container = DroolsConfig.buildFraudKieContainer();

    public KieContainer get() {
        return container;
    }

    public void replace(KieContainer newContainer) {
        this.container = newContainer;
    }
}
