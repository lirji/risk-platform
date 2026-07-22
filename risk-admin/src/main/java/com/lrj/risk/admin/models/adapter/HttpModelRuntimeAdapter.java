package com.lrj.risk.admin.models.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.admin.models.application.ModelRuntimePort;
import com.lrj.risk.admin.security.GatewayAccessTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpModelRuntimeAdapter implements ModelRuntimePort {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper;
    private final String gatewayBaseUrl;
    private final GatewayAccessTokenProvider accessTokenProvider;

    public HttpModelRuntimeAdapter(ObjectMapper mapper,
            @Value("${risk.gateway.base-url:http://localhost:8082}") String gatewayBaseUrl,
            GatewayAccessTokenProvider accessTokenProvider) {
        this.mapper = mapper; this.gatewayBaseUrl = gatewayBaseUrl; this.accessTokenProvider = accessTokenProvider;
    }

    @Override
    public void activate(String version, String artifactUri, String checksum, int rolloutPercentage) {
        try {
            String body = mapper.writeValueAsString(Map.of("version", version,
                    "artifactUri", artifactUri, "checksum", checksum,
                    "rolloutPercentage", rolloutPercentage));
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(gatewayBaseUrl + "/internal/v1/model-deployments"))
                    .header("Content-Type", "application/json").timeout(java.time.Duration.ofSeconds(15));
            String token = accessTokenProvider.bearerToken();
            if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
            HttpResponse<String> response = http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("model runtime rejected activation: " + response.body());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (Exception exception) {
            throw new IllegalStateException("model runtime activation failed", exception);
        }
    }
}
