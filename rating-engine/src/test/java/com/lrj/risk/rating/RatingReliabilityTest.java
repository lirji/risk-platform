package com.lrj.risk.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.Optional;

import com.lrj.risk.rating.config.ConfigRepository;
import com.lrj.risk.rating.config.RatingTask;
import com.lrj.risk.rating.es.EsClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class RatingReliabilityTest {

    @Test
    void concurrentWorkersAtomicallyClaimOnlyOneTask() throws Exception {
        String url = "jdbc:h2:mem:rating-claim;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE t_rating_task(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY, task_code VARCHAR(64), model_code VARCHAR(64),
                      source_index VARCHAR(128), target_index VARCHAR(128), status VARCHAR(16),
                      attempts INT, max_attempts INT, lease_owner VARCHAR(128), lease_until TIMESTAMP,
                      last_error VARCHAR(1000))
                    """);
            statement.execute("INSERT INTO t_rating_task(task_code,model_code,source_index,target_index,status,attempts,max_attempts) VALUES('t','m','s','d','PENDING',0,3)");
        }
        ConfigRepository first = new ConfigRepository(url, "sa", "");
        ConfigRepository second = new ConfigRepository(url, "sa", "");
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Optional<RatingTask>>> claims = List.of(
                    () -> first.claimNextPendingTask("worker-1", Duration.ofMinutes(5)),
                    () -> second.claimNextPendingTask("worker-2", Duration.ofMinutes(5)));
            var results = executor.invokeAll(claims);
            long claimed = results.stream().filter(future -> {
                try { return future.get().isPresent(); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).count();
            assertEquals(1, claimed);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void bulkResponseWithOneFailedItemFailsWholeTaskWithItemEvidence() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/risk/_bulk", exchange -> {
            byte[] response = "{\"errors\":true,\"items\":[{\"index\":{\"_id\":\"c1\",\"status\":201}},{\"index\":{\"_id\":\"c2\",\"status\":429,\"error\":{\"type\":\"rejected\"}}}]}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            EsClient client = new EsClient("http://localhost:" + server.getAddress().getPort());
            var failure = assertThrows(EsClient.BulkWriteException.class, () -> client.bulkIndex("risk", List.of(
                    Map.of("_id", "c1", "score", 1), Map.of("_id", "c2", "score", 2))));
            assertEquals(1, failure.failures().size());
        } finally {
            server.stop(0);
        }
    }
}
