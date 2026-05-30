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

    public HotReloadService(KieContainerHolder holder) {
        this.holder = holder;
    }

    /** 用 classpath 基线规则 + 传入的额外 DRL 重建并切换容器; 编译失败抛 IllegalStateException。 */
    public void reload(String extraDrl) {
        KieContainer rebuilt = DroolsConfig.build(extraDrl);  // 编译失败在此抛出
        holder.replace(rebuilt);
        log.info("规则热发布成功, 容器已切换");
    }
}
