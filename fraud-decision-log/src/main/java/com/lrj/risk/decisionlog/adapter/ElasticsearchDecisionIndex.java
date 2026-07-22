package com.lrj.risk.decisionlog.adapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.v1.DecisionEventV1;
import com.lrj.risk.decisionlog.application.DecisionIndexPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchDecisionIndex implements DecisionIndexPort {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String index;

    public ElasticsearchDecisionIndex(ObjectMapper mapper,
            @Value("${risk.elasticsearch.base-url:http://localhost:9200}") String baseUrl,
            @Value("${risk.elasticsearch.decision-index:risk-decisions-v1}") String index) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.index = index;
    }

    @Override
    public void index(DecisionEventV1 event) {
        request("PUT", "/" + index + "/_doc/" + event.decisionId(), event);
    }

    @Override
    public Map<String, Object> search(String riskLevel, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", Math.max(0, page) * safeSize);
        body.put("size", safeSize);
        body.put("sort", java.util.List.of(Map.of("metadata.occurredAt", Map.of("order", "desc"))));
        body.put("query", riskLevel == null || riskLevel.isBlank()
                ? Map.of("match_all", Map.of())
                : Map.of("term", Map.of("riskLevel", riskLevel)));
        return request("POST", "/" + index + "/_search", body);
    }

    @Override
    public Map<String, Object> detail(String decisionId) {
        return request("GET", "/" + index + "/_doc/" + decisionId, null);
    }

    private Map<String, Object> request(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(java.time.Duration.ofSeconds(5)).header("Content-Type", "application/json");
            if ("GET".equals(method)) builder.GET();
            else builder.method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Elasticsearch HTTP " + response.statusCode() + ": " + response.body());
            }
            return mapper.readValue(response.body(), new TypeReference<>() { });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Elasticsearch request interrupted", interrupted);
        } catch (Exception exception) {
            throw new IllegalStateException("Elasticsearch request failed", exception);
        }
    }
}
