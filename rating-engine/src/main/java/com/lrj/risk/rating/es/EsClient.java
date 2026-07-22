package com.lrj.risk.rating.es;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Elasticsearch REST adapter with search_after pagination and per-item bulk failure detection. */
public class EsClient implements Serializable {

    private static final int PAGE_SIZE = 1_000;
    private static final int BULK_SIZE = 500;
    private final String baseUrl;
    private transient HttpClient http;
    private transient ObjectMapper mapper;

    public EsClient(String baseUrl) { this.baseUrl = baseUrl; }
    private HttpClient http() { if (http == null) http = HttpClient.newHttpClient(); return http; }
    private ObjectMapper mapper() { if (mapper == null) mapper = new ObjectMapper(); return mapper; }

    public List<Map<String, Object>> fetchAll(String index) {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            JsonNode searchAfter = null;
            while (true) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("size", PAGE_SIZE);
                body.put("query", Map.of("match_all", Map.of()));
                body.put("sort", List.of(Map.of("_shard_doc", "asc")));
                if (searchAfter != null) {
                    body.put("search_after", mapper().convertValue(searchAfter, List.class));
                }
                JsonNode response = sendJson(index + "/_search", mapper().writeValueAsString(body));
                JsonNode hits = response.path("hits").path("hits");
                if (hits.isEmpty()) break;
                for (JsonNode hit : hits) {
                    rows.add(mapper().convertValue(hit.path("_source"), Map.class));
                    searchAfter = hit.path("sort");
                }
                if (hits.size() < PAGE_SIZE) break;
            }
            return rows;
        } catch (Exception exception) {
            throw new RuntimeException("拉取标签失败: " + index, exception);
        }
    }

    public void bulkIndex(String index, List<Map<String, Object>> docs) {
        for (int from = 0; from < docs.size(); from += BULK_SIZE) {
            writeChunk(index, docs.subList(from, Math.min(docs.size(), from + BULK_SIZE)));
        }
    }

    private void writeChunk(String index, List<Map<String, Object>> docs) {
        try {
            StringBuilder body = new StringBuilder();
            for (Map<String, Object> source : docs) {
                Map<String, Object> content = new LinkedHashMap<>(source);
                Object id = content.remove("_id");
                if (id == null) throw new IllegalArgumentException("bulk document _id is required");
                body.append(mapper().writeValueAsString(Map.of("index", Map.of("_id", id)))).append('\n');
                body.append(mapper().writeValueAsString(content)).append('\n');
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + index + "/_bulk?refresh=true"))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("ES bulk HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode result = mapper().readTree(response.body());
            if (result.path("errors").asBoolean(false)) {
                List<String> failures = new ArrayList<>();
                for (JsonNode item : result.path("items")) {
                    JsonNode operation = item.elements().next();
                    if (operation.path("status").asInt() >= 300) {
                        failures.add(operation.path("_id").asText() + ":" + operation.path("error").toString());
                    }
                }
                throw new BulkWriteException(failures);
            }
        } catch (BulkWriteException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("写入风险库失败: " + index, exception);
        }
    }

    private JsonNode sendJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("ES HTTP " + response.statusCode() + ": " + response.body());
        }
        return mapper().readTree(response.body());
    }

    public static class BulkWriteException extends RuntimeException {
        private final List<String> failures;
        public BulkWriteException(List<String> failures) {
            super("Elasticsearch bulk contained item failures: " + failures);
            this.failures = List.copyOf(failures);
        }
        public List<String> failures() { return failures; }
    }
}
