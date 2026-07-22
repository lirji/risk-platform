package com.lrj.risk.admin.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 统一身份平台签发 token 的强校验契约。 */
@ConfigurationProperties(prefix = "risk.identity")
public class RiskIdentityProperties {
    private String issuer = "http://localhost:8000";
    private String jwkSetUri = "http://localhost:8000/.well-known/jwks";
    private String organization = "risk-platform";
    private List<String> audiences = new ArrayList<>(List.of(
            "ragshared0client00000001-org-risk-platform"));

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public List<String> getAudiences() { return audiences; }
    public void setAudiences(List<String> audiences) { this.audiences = audiences; }
}
