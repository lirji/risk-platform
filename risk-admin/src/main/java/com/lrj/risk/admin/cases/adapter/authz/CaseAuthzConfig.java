package com.lrj.risk.admin.cases.adapter.authz;

import com.lrj.authz.protocol.AuthzEngine;
import com.lrj.risk.admin.cases.application.CaseAuthorization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CaseAuthzProperties.class)
public class CaseAuthzConfig {
    @Bean
    CaseAuthorization caseAuthorization(CaseAuthzProperties properties,
                                        ObjectProvider<AuthzEngine> engines) {
        return new RemoteCaseAuthorization(properties.getMode(), engines);
    }
}
