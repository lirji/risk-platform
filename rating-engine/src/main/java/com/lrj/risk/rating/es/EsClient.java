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

/**
 * 轻量 ES REST 客户端 (不引 elasticsearch-spark 连接器, 避开其与 Spark 4 的兼容问题)。
 * 读标签源 (search) + 写 es 风险库 (bulk)。
 */
public class EsClient implements Serializable {

    private final String baseUrl;
    private transient HttpClient http;
    private transient ObjectMapper mapper;

    public EsClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private HttpClient http() {
        if (http == null) {
            http = HttpClient.newHttpClient();
        }
        return http;
    }

    private ObjectMapper mapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        return mapper;
    }

    /** 拉取某索引全部文档 (size=10000, 演示足够; 生产用 scroll/search_after)。 */
    public List<Map<String, Object>> fetchAll(String index) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + index + "/_search?size=10000"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\":{\"match_all\":{}}}"))
                    .build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("ES 查询失败 HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode hits = mapper().readTree(resp.body()).path("hits").path("hits");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode hit : hits) {
                Map<String, Object> row = mapper().convertValue(hit.path("_source"), Map.class);
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            throw new RuntimeException("拉取标签失败: " + index, e);
        }
    }

    /** 批量写入 es 风险库; docs 每个含 "_id" 键作文档 ID, 其余为内容。 */
    public void bulkIndex(String index, List<Map<String, Object>> docs) {
        try {
            StringBuilder body = new StringBuilder();
            for (Map<String, Object> doc : docs) {
                Map<String, Object> content = new LinkedHashMap<>(doc);
                Object id = content.remove("_id");
                body.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n");
                body.append(mapper().writeValueAsString(content)).append("\n");
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + index + "/_bulk?refresh=true"))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("ES 写入失败 HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("写入风险库失败: " + index, e);
        }
    }
}
