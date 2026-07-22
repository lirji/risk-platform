package com.lrj.risk.fraud.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.feature.application.port.FeatureReader;
import com.lrj.risk.feature.domain.FeatureSnapshot;
import com.lrj.risk.fraud.gateway.decision.application.model.EvaluateTransactionCommand;
import com.lrj.risk.fraud.gateway.decision.application.model.RiskDecisionResult;
import com.lrj.risk.fraud.gateway.decision.application.TransactionalDecisionService;
import com.lrj.risk.contracts.kernel.TransactionKey;
import com.lrj.risk.fraud.gateway.decision.application.port.in.EvaluateTransactionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "risk.outbox.relay-delay-ms=600000")
@AutoConfigureMockMvc
class RiskDecisionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired EvaluateTransactionUseCase useCase;
    @Autowired TransactionalDecisionService transactionalService;
    @MockBean FeatureReader featureReader;
    @MockBean KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM risk_decision");
        when(featureReader.fetch(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(FeatureSnapshot.empty());
    }

    @Test
    void validRetryReturnsFirstDecisionAndCreatesExactlyTwoEvents() throws Exception {
        String body = requestJson("txn-idempotent", Instant.now());
        String firstBody = mvc.perform(post("/api/v1/risk/evaluations")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionId").isNotEmpty())
                .andExpect(jsonPath("$.txnId").value("txn-idempotent"))
                .andExpect(jsonPath("$.ruleVersion").isNotEmpty())
                .andExpect(jsonPath("$.modelVersion").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secondBody = mvc.perform(post("/api/v1/risk/evaluations")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode first = objectMapper.readTree(firstBody);
        JsonNode second = objectMapper.readTree(secondBody);
        assertThat(second.path("decisionId").asText()).isEqualTo(first.path("decisionId").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM risk_decision", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateCollapsesToOneDecision() throws Exception {
        EvaluateTransactionCommand command = command("txn-concurrent", Instant.now());
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<RiskDecisionResult>> calls = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                calls.add(() -> useCase.evaluate(command));
            }
            Set<String> decisionIds = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get(10, TimeUnit.SECONDS).decisionId();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    }).collect(Collectors.toSet());
            assertThat(decisionIds).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM risk_decision", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseUniquenessCollapsesCrossInstanceStyleRaceAfterLoserTransactionRollsBack() throws Exception {
        EvaluateTransactionCommand command = command("txn-database-race", Instant.now());
        TransactionKey key = new TransactionKey(command.sourceId(), command.txnId());
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<RiskDecisionResult>> calls = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> (Callable<RiskDecisionResult>) () -> transactionalService.evaluate(command, key))
                    .toList();
            Set<String> decisionIds = executor.invokeAll(calls).stream().map(future -> {
                try { return future.get(10, TimeUnit.SECONDS).decisionId(); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).collect(Collectors.toSet());
            assertThat(decisionIds).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM risk_decision", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unavailableFeaturesFailSafeInsteadOfAllowing() {
        when(featureReader.fetch("account-1")).thenReturn(FeatureSnapshot.unavailable());
        RiskDecisionResult result = useCase.evaluate(command("txn-degraded", Instant.now()));
        assertThat(result.action()).isEqualTo("CHALLENGE");
        assertThat(result.hitRules()).contains("DEGRADED_FEATURE_UNAVAILABLE");
    }

    @Test
    void invalidFieldsAndFutureEventReturnStableValidationError() throws Exception {
        mvc.perform(post("/api/v1/risk/evaluations")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.txnId").exists());

        mvc.perform(post("/api/v1/risk/evaluations")
                        .contentType("application/json")
                        .content(requestJson("txn-future", Instant.now().plusSeconds(3600))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String requestJson(String txnId, Instant eventTime) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.ofEntries(
                java.util.Map.entry("sourceId", "MOBILE_TRANSFER"),
                java.util.Map.entry("txnId", txnId),
                java.util.Map.entry("channel", "MOBILE"),
                java.util.Map.entry("bizType", "TRANSFER"),
                java.util.Map.entry("accountNo", "account-1"),
                java.util.Map.entry("counterpartyAccount", "account-2"),
                java.util.Map.entry("amount", 10_000),
                java.util.Map.entry("currency", "CNY"),
                java.util.Map.entry("deviceId", "device-1"),
                java.util.Map.entry("ip", "127.0.0.1"),
                java.util.Map.entry("eventTime", eventTime.toString())));
    }

    private EvaluateTransactionCommand command(String txnId, Instant eventTime) {
        return new EvaluateTransactionCommand("MOBILE_TRANSFER", txnId, "MOBILE", "TRANSFER",
                "account-1", "account-2", 10_000, "CNY", "device-1", "127.0.0.1",
                eventTime, com.lrj.risk.contracts.v1.TransactionStatus.UNKNOWN, "correlation-1");
    }
}
