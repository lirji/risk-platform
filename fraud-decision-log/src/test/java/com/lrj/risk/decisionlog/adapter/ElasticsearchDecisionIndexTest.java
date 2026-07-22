package com.lrj.risk.decisionlog.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElasticsearchDecisionIndexTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private ElasticsearchDecisionIndex index;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/risk-decisions-v1/_search", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}");
        });
        server.createContext("/risk-decisions-v1/_doc/decision-1",
                exchange -> respond(exchange, 200, "{\"_id\":\"decision-1\"}"));
        server.start();
        index = new ElasticsearchDecisionIndex(mapper,
                "http://127.0.0.1:" + server.getAddress().getPort(), "risk-decisions-v1");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void usesKeywordMappingAndBoundsPagination() throws Exception {
        var response = index.search("HIGH", -3, 500);
        JsonNode body = mapper.readTree(requestBody.get());

        assertThat(body.path("from").asInt()).isZero();
        assertThat(body.path("size").asInt()).isEqualTo(100);
        assertThat(body.at("/query/term/riskLevel").asText()).isEqualTo("HIGH");
        assertThat(response).containsKey("hits");
        assertThat(index.detail("decision-1")).containsEntry("_id", "decision-1");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
