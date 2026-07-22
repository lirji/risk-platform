package com.lrj.risk.admin.rules.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.admin.rules.application.port.RuleRuntimePort;
import com.lrj.risk.admin.rules.domain.RuleRelease;
import com.lrj.risk.admin.security.GatewayAccessTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpRuleRuntimeAdapter implements RuleRuntimePort {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String gatewayBaseUrl;
    private final GatewayAccessTokenProvider accessTokenProvider;

    public HttpRuleRuntimeAdapter(ObjectMapper objectMapper,
                                  @Value("${risk.gateway.base-url:http://localhost:8082}") String gatewayBaseUrl,
                                  GatewayAccessTokenProvider accessTokenProvider) {
        this.objectMapper = objectMapper;
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.accessTokenProvider = accessTokenProvider;
    }

    @Override
    public void activate(String sourceId, RuleRelease release, List<String> ruleSets, int rolloutPercentage,
                         RuleRelease previous, RuleRelease shadow) {
        try {
            Map<String, Object> deployment = new java.util.LinkedHashMap<>();
            deployment.put("sourceId", sourceId);
            deployment.put("version", release.ruleCode() + "-" + release.version());
            deployment.put("ruleSets", ruleSets);
            deployment.put("drl", release.drl());
            deployment.put("rolloutPercentage", rolloutPercentage);
            if (previous != null) {
                deployment.put("previousVersion", previous.ruleCode() + "-" + previous.version());
                deployment.put("previousRuleSets", ruleSets);
                deployment.put("previousDrl", previous.drl());
            }
            if (shadow != null) {
                deployment.put("shadowVersion", shadow.ruleCode() + "-" + shadow.version());
                deployment.put("shadowRuleSets", ruleSets);
                deployment.put("shadowDrl", shadow.drl());
            }
            String body = objectMapper.writeValueAsString(deployment);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayBaseUrl + "/internal/v1/rule-deployments"))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(5));
            String token = accessTokenProvider.bearerToken();
            if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("rule runtime rejected release: " + response.body());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("rule runtime activation interrupted", interrupted);
        } catch (Exception exception) {
            throw new IllegalStateException("rule runtime activation failed", exception);
        }
    }
}
