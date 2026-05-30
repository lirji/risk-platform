package com.lrj.risk.fraud.engine;

import java.io.IOException;
import java.io.InputStream;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.Message;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Drools 容器构建工具。
 *
 * <p>不用 {@code getKieClasspathContainer()}: 它扫 classpath URL 解析 kmodule.xml,
 * 在 Spring Boot 重打包的 fat jar 里 (jar:nested: 协议) 读取会失败。
 * 改为程序化 {@link org.kie.api.builder.KieFileSystem} 构建, DRL 经 Spring 的
 * {@link PathMatchingResourcePatternResolver} 加载 (兼容 fat jar 与展开目录), 思路同 drools-demo。
 *
 * <p>容器由 {@link KieContainerHolder} 持有并可热替换 (规则热发布)。
 */
public final class DroolsConfig {

    private DroolsConfig() {
    }

    /** 用 classpath 上的规则资源构建容器。 */
    public static KieContainer buildFraudKieContainer() {
        return build(null);
    }

    /**
     * 构建容器, 可附加一段运行时提供的 DRL (规则热发布)。
     *
     * @param extraDrl 额外 DRL 文本 (须声明 package rules.fraud + import); 为 null 则只用 classpath 规则
     * @throws IllegalStateException 编译失败时抛出, 消息含出错文件/行列号
     */
    public static KieContainer build(String extraDrl) {
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

            // CSV 决策表: 需显式标 DTABLE + CSV 输入类型
            Resource[] csvs = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:rules/**/*.csv");
            for (Resource csv : csvs) {
                byte[] content;
                try (InputStream in = csv.getInputStream()) {
                    content = in.readAllBytes();
                }
                DecisionTableConfiguration dtc = KnowledgeBuilderFactory.newDecisionTableConfiguration();
                dtc.setInputType(DecisionTableInputType.CSV);
                // 单参 write(Resource) + sourcePath, 保留 DTABLE 类型与 CSV 配置
                // (两参 write(path, resource) 会丢掉 resourceType/configuration → 决策表不被编译)
                org.kie.api.io.Resource res = ks.getResources().newByteArrayResource(content)
                        .setResourceType(ResourceType.DTABLE)
                        .setConfiguration(dtc);
                res.setSourcePath("src/main/resources/rules/fraud/" + csv.getFilename());
                kfs.write(res);
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载规则资源失败", e);
        }

        // 运行时热发布的额外规则
        if (extraDrl != null && !extraDrl.isBlank()) {
            kfs.write("src/main/resources/rules/fraud/hotreload.drl",
                    extraDrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        if (kb.getResults().hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("DRL 编译失败: " + kb.getResults().getMessages());
        }
        return ks.newKieContainer(kb.getKieModule().getReleaseId());
    }
}
