package com.lrj.risk.fraud.engine;

import java.io.IOException;
import java.io.InputStream;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.Message;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Drools 容器配置。
 *
 * <p>不用 {@code getKieClasspathContainer()}: 它扫 classpath URL 解析 kmodule.xml,
 * 在 Spring Boot 重打包的 fat jar 里 (jar:nested: 协议) 读取会失败。
 * 改为程序化 {@link org.kie.api.builder.KieFileSystem} 构建, DRL 经 Spring 的
 * {@link PathMatchingResourcePatternResolver} 加载 (兼容 fat jar 与展开目录), 思路同 drools-demo。
 *
 * <p>编译昂贵, 容器单例复用; 后续接 KieScanner + KJAR 做规则热发布 (drools-demo Step 16)。
 */
@Configuration
public class DroolsConfig {

    @Bean
    public KieContainer fraudKieContainer() {
        return buildFraudKieContainer();
    }

    /** 供 @Bean 与测试共用, 保证两边走同一条构建路径。 */
    public static KieContainer buildFraudKieContainer() {
        KieServices ks = KieServices.get();

        // 程序化声明 kbase/ksession (替代 META-INF/kmodule.xml)
        KieModuleModel kmm = ks.newKieModuleModel();
        kmm.newKieBaseModel("fraudKBase")
                .addPackage("rules.fraud")
                .setDefault(true)
                .newKieSessionModel("fraudSession")
                .setDefault(true);

        var kfs = ks.newKieFileSystem();
        kfs.writeKModuleXML(kmm.toXML());

        try {
            Resource[] drls = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:rules/**/*.drl");
            if (drls.length == 0) {
                throw new IllegalStateException("classpath 下未找到任何 DRL (rules/**/*.drl)");
            }
            for (Resource drl : drls) {
                // 读全字节: KieBuilder.buildAll() 是惰性读资源, 不能传会被提前关闭的流 (否则 ClosedChannelException)
                byte[] content;
                try (InputStream in = drl.getInputStream()) {
                    content = in.readAllBytes();
                }
                kfs.write("src/main/resources/rules/fraud/" + drl.getFilename(), content);
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载 DRL 资源失败", e);
        }

        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        if (kb.getResults().hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("DRL 编译失败: " + kb.getResults().getMessages());
        }
        return ks.newKieContainer(kb.getKieModule().getReleaseId());
    }
}
