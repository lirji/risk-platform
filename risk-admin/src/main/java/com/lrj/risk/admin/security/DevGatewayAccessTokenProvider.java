package com.lrj.risk.admin.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevGatewayAccessTokenProvider implements GatewayAccessTokenProvider {
    @Override
    public String bearerToken() {
        return "";
    }
}
