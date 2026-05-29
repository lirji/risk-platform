package com.lrj.risk.fraud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 反欺诈接入层启动类。
 *
 * <p>扫描整个 com.lrj.risk 包, 纳入 common-feature (RedisFeatureClient) 与
 * fraud-engine (DroolsConfig / FraudEngineService / SourceRuleSetBinding) 的 Bean。
 */
@SpringBootApplication(scanBasePackages = "com.lrj.risk")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
