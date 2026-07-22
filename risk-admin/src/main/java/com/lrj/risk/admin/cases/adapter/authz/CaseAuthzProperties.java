package com.lrj.risk.admin.cases.adapter.authz;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "risk.authz")
public class CaseAuthzProperties {
    private String mode = "disabled";

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
