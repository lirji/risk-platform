package com.lrj.risk.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.lrj.risk.admin.operations.adapter.JdbcDeadEventAdapter;
import com.lrj.risk.admin.operations.application.OperationsService;
import com.lrj.risk.admin.cases.application.CaseWorkflowService;
import com.lrj.risk.admin.decisions.application.DecisionQuery;
import com.lrj.risk.admin.models.application.ModelRegistryService;
import com.lrj.risk.admin.models.application.ModelRuntimePort;
import com.lrj.risk.admin.rules.application.RuleReleaseService;
import com.lrj.risk.admin.rules.application.port.RuleRuntimePort;
import com.lrj.risk.admin.rules.domain.RuleReleaseStatus;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class AdminContextIntegrationTest {

    @Autowired RuleReleaseService rules;
    @Autowired ModelRegistryService models;
    @Autowired CaseWorkflowService cases;
    @Autowired DecisionQuery decisions;
    @Autowired JdbcDeadEventAdapter deadEventAdapter;
    @Autowired OperationsService operations;
    @Autowired JdbcTemplate jdbc;
    @MockBean RuleRuntimePort runtime;
    @MockBean ModelRuntimePort modelRuntime;
    @MockBean KafkaTemplate<String, String> kafka;

    @Test
    void ruleReleaseUsesReviewGateAndActivatesRuntimeBeforeBinding() {
        var release = rules.create("transfer", "Transfer rules", validDrl(), "author");
        rules.submit(release.releaseId(), "author");
        rules.approve(release.releaseId(), "reviewer");
        var published = rules.publish(release.releaseId(), "BANK_A", List.of("threshold"),
                100, null, "publisher");
        assertThat(published.status()).isEqualTo(RuleReleaseStatus.PUBLISHED);
        verify(runtime).activate("BANK_A", published, List.of("threshold"), 100, null, null);
        assertThat(jdbc.queryForObject("SELECT active_release_id FROM source_rule_binding WHERE source_id='BANK_A'",
                String.class)).isEqualTo(release.releaseId());
    }

    @Test
    void caseResolutionWritesTrainingLabelAndAudit() {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO risk_case(case_id, decision_id, source_id, txn_id, risk_level, status,
                                      version, created_at, updated_at)
                VALUES ('case-1', 'decision-1', 'BANK_A', 'txn-1', 'HIGH', 'OPEN', 0, ?, ?)
                """, Timestamp.from(now), Timestamp.from(now));
        cases.claim("risk-platform", "case-1", "analyst");
        cases.comment("risk-platform", "case-1", "analyst", "confirmed by callback");
        cases.resolve("risk-platform", "case-1", "analyst", "FRAUD", "customer denied transaction");
        assertThat(jdbc.queryForObject("SELECT label FROM case_label_feedback WHERE case_id='case-1'",
                String.class)).isEqualTo("FRAUD");
    }

    @Test
    void modelRegistryRequiresMetricsAndSeparationOfDuties() {
        String id = models.register("fraud-rf", 1, "s3://models/fraud-rf-1.pmml", "checksum-1",
                Map.of("auc", 0.91, "ks", 0.52, "recallAtFixedFpr", 0.70,
                        "precision", 0.78, "recall", 0.72), "facts-v1", "trainer");
        models.approve(id, "reviewer");
        models.activate(id, "operator");
        assertThat(jdbc.queryForObject("SELECT status FROM model_version WHERE model_id=?", String.class, id))
                .isEqualTo("ACTIVE");

        String canaryId = models.register("fraud-rf", 2, "s3://models/fraud-rf-2.pmml", "checksum-2",
                Map.of("auc", 0.92, "ks", 0.54, "recallAtFixedFpr", 0.72,
                        "precision", 0.79, "recall", 0.73), "facts-v2", "trainer");
        models.approve(canaryId, "reviewer");
        models.activate(canaryId, "operator", 30);
        assertThat(jdbc.queryForMap("SELECT status, rollout_percentage FROM model_version WHERE model_id=?", canaryId))
                .containsEntry("status", "CANARY").containsEntry("rollout_percentage", 30);
        assertThat(jdbc.queryForMap("SELECT status, rollout_percentage FROM model_version WHERE model_id=?", id))
                .containsEntry("status", "STABLE").containsEntry("rollout_percentage", 70);

        models.activate(canaryId, "operator", 100);
        assertThat(jdbc.queryForMap("SELECT status, rollout_percentage FROM model_version WHERE model_id=?", canaryId))
                .containsEntry("status", "ACTIVE").containsEntry("rollout_percentage", 100);
        assertThat(jdbc.queryForObject("SELECT status FROM model_version WHERE model_id=?", String.class, id))
                .isEqualTo("RETIRED");
    }

    @Test
    void decisionReadModelSupportsTransactionAndRiskFilters() {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO risk_decision(decision_id, source_id, txn_id, correlation_id, event_time,
                    account_token, risk_level, action, fraud_score, hit_rules_json,
                    feature_snapshot_json, rule_version, model_version, cost_ms, created_at)
                VALUES ('decision-query-1', 'BANK_Q', 'txn-query-1', 'corr-query-1', ?,
                    'acct-token', 'HIGH', 'REVIEW', 0.91, '[]', '{}', 'rules-1', 'model-1', 8, ?)
                """, Timestamp.from(now), Timestamp.from(now));

        var page = decisions.search("HIGH", "txn-query-1", 0, 20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.content()).extracting(DecisionQuery.DecisionView::decisionId)
                .containsExactly("decision-query-1");
        assertThat(decisions.search("LOW", "txn-query-1", 0, 20).total()).isZero();
    }

    @Test
    void kafkaDeadLetterIsCataloguedAndReplayedToOriginalTopicWithAudit() {
        String payload = "{\"eventId\":\"event-42\"}";
        deadEventAdapter.catalog(new ConsumerRecord<>("transaction.v1.DLT", 0, 42L, "account-token", payload));
        assertThat(operations.deadEvents()).filteredOn(event -> "KAFKA_DLT".equals(event.get("event_kind")))
                .singleElement().extracting(event -> event.get("topic")).isEqualTo("transaction.v1");

        when(kafka.send("transaction.v1", "account-token", payload))
                .thenReturn(CompletableFuture.completedFuture(null));
        operations.replay("dlt-transaction.v1.DLT-0-42", "ops-admin");

        verify(kafka).send("transaction.v1", "account-token", payload);
        assertThat(jdbc.queryForObject("SELECT status FROM dead_letter_event WHERE event_id=?", String.class,
                "dlt-transaction.v1.DLT-0-42")).isEqualTo("REPLAYED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE action='DEAD_EVENT_REPLAY_REQUESTED'
                 AND resource_id='dlt-transaction.v1.DLT-0-42'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void ruleReleaseSupportsDeterministicCanaryAndRollback() {
        var first = rules.create("canary-transfer", "Canary rules v1", validDrl(), "author-a");
        rules.submit(first.releaseId(), "author-a");
        rules.approve(first.releaseId(), "reviewer-a");
        rules.publish(first.releaseId(), "BANK_CANARY", List.of("threshold"), 100, null, "publisher");

        var second = rules.create("canary-transfer", "Canary rules v2", validDrl(), "author-b");
        rules.submit(second.releaseId(), "author-b");
        rules.approve(second.releaseId(), "reviewer-b");
        clearInvocations(runtime);
        rules.publish(second.releaseId(), "BANK_CANARY", List.of("threshold"), 20, null, "publisher");
        verify(runtime).activate("BANK_CANARY", second, List.of("threshold"), 20, first, null);

        rules.rollback(second.releaseId(), "BANK_CANARY", "publisher");
        verify(runtime).activate("BANK_CANARY", first, List.of("threshold"), 100, second, null);
        assertThat(rules.bindings()).filteredOn(binding -> binding.sourceId().equals("BANK_CANARY"))
                .singleElement().extracting(binding -> binding.activeReleaseId())
                .isEqualTo(first.releaseId());
    }

    private String validDrl() {
        return "package rules.fraud; rule \"noop\" agenda-group \"threshold\" when then end";
    }
}
