package com.lrj.risk.profiling.offline;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.api.java.function.ForeachPartitionFunction;
import redis.clients.jedis.Jedis;

/** Partition-local serving projection; failures fail the Spark task and are retried by Spark. */
final class ProfileProjectionWriter implements ForeachPartitionFunction<String>, Serializable {

    private final String esUrl;
    private final String redisHost;
    private final int redisPort;

    ProfileProjectionWriter(String esUrl, String redisHost, int redisPort) {
        this.esUrl = esUrl; this.redisHost = redisHost; this.redisPort = redisPort;
    }

    @Override
    public void call(Iterator<String> rows) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpClient http = HttpClient.newHttpClient();
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            while (rows.hasNext()) {
                Map<String, Object> profile = mapper.readValue(rows.next(), new TypeReference<>() { });
                String customerId = String.valueOf(profile.get("customer_id"));
                Map<String, String> strings = new LinkedHashMap<>();
                profile.forEach((key, value) -> strings.put(key, String.valueOf(value)));
                jedis.hset("offline-profile:{" + customerId + "}", strings);
                HttpRequest request = HttpRequest.newBuilder(URI.create(esUrl + "/customer-profiles/_doc/" + customerId))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(profile))).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("profile ES projection failed HTTP " + response.statusCode());
                }
            }
        }
    }
}
