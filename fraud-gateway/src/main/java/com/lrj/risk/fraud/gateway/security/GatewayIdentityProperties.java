package com.lrj.risk.fraud.gateway.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 统一 Casdoor 机器身份边界；权限由受众绑定，不能信任客户端可自填的 scope。 */
@ConfigurationProperties(prefix = "risk.identity")
public class GatewayIdentityProperties {
    private String issuer = "http://localhost:8000";
    private String jwkSetUri = "http://localhost:8000/.well-known/jwks";
    private String organization = "risk-platform";
    private List<String> evaluationAudiences = new ArrayList<>(List.of("risk-bank-client"));
    private List<String> runtimeAudiences = new ArrayList<>(List.of("risk-runtime-client"));

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public List<String> getEvaluationAudiences() { return evaluationAudiences; }
    public void setEvaluationAudiences(List<String> evaluationAudiences) { this.evaluationAudiences = evaluationAudiences; }
    public List<String> getRuntimeAudiences() { return runtimeAudiences; }
    public void setRuntimeAudiences(List<String> runtimeAudiences) { this.runtimeAudiences = runtimeAudiences; }

    public List<String> allAudiences() {
        List<String> result = new ArrayList<>(evaluationAudiences);
        result.addAll(runtimeAudiences);
        return result;
    }
}
