package com.lrj.risk.fraud.gateway.decision.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.kernel.TransactionKey;
import com.lrj.risk.contracts.v1.DecisionAction;
import com.lrj.risk.contracts.v1.DecisionEventV1;
import com.lrj.risk.contracts.v1.DecisionRiskLevel;
import com.lrj.risk.contracts.v1.EventMetadataV1;
import com.lrj.risk.contracts.v1.RuleHitV1;
import com.lrj.risk.contracts.v1.TransactionEventV1;
import com.lrj.risk.feature.application.port.FeatureReader;
import com.lrj.risk.feature.domain.FeatureSnapshot;
import com.lrj.risk.fraud.application.FraudDecisionService;
import com.lrj.risk.fraud.domain.model.RiskAssessment;
import com.lrj.risk.fraud.domain.model.RiskLevel;
import com.lrj.risk.fraud.domain.model.TransactionEvent;
import com.lrj.risk.fraud.gateway.decision.application.model.EvaluateTransactionCommand;
import com.lrj.risk.fraud.gateway.decision.application.model.RiskDecisionResult;
import com.lrj.risk.fraud.gateway.decision.application.port.out.AccountTokenizationPort;
import com.lrj.risk.fraud.gateway.decision.application.port.out.DecisionRepository;
import com.lrj.risk.fraud.gateway.decision.application.port.out.DecisionTelemetry;
import com.lrj.risk.fraud.gateway.decision.domain.DecisionRecord;
import com.lrj.risk.fraud.gateway.decision.domain.OutboxMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TransactionalDecisionService {

    private final DecisionRepository repository;
    private final FeatureReader featureReader;
    private final FraudDecisionService fraudDecisionService;
    private final AccountTokenizationPort accountTokenizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration maxEventAge;
    private final Duration maxFutureSkew;
    private final DecisionTelemetry telemetry;
    private final DecisionCommitService commitService;

    public TransactionalDecisionService(
            DecisionRepository repository,
            FeatureReader featureReader,
            FraudDecisionService fraudDecisionService,
            AccountTokenizationPort accountTokenizer,
            ObjectMapper objectMapper,
            Clock clock,
            DecisionTelemetry telemetry,
            DecisionCommitService commitService,
            @Value("${risk.decision.max-event-age:P90D}") Duration maxEventAge,
            @Value("${risk.decision.max-future-skew:PT5M}") Duration maxFutureSkew) {
        this.repository = repository;
        this.featureReader = featureReader;
        this.fraudDecisionService = fraudDecisionService;
        this.accountTokenizer = accountTokenizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.telemetry = telemetry;
        this.commitService = commitService;
        this.maxEventAge = maxEventAge;
        this.maxFutureSkew = maxFutureSkew;
    }

    public RiskDecisionResult evaluate(EvaluateTransactionCommand command, TransactionKey key) {
        long startedAt = System.nanoTime();
        var existing = repository.findByTransactionKey(key);
        if (existing.isPresent()) {
            return existing.get().toResult();
        }
        Instant now = clock.instant();
        validateEventTime(command.eventTime(), now);

        long stageStarted = System.nanoTime();
        FeatureSnapshot features = featureReader.fetch(command.accountNo());
        telemetry.stageDuration("feature", System.nanoTime() - stageStarted);
        TransactionEvent transaction = toDomain(command);
        RiskAssessment assessment;
        String modelVersion;
        String ruleVersion;
        try {
            if (!features.isAvailable()) {
                assessment = safeAssessment(RiskLevel.MEDIUM, "DEGRADED_FEATURE_UNAVAILABLE");
                modelVersion = "not-evaluated";
            } else {
                stageStarted = System.nanoTime();
                assessment = fraudDecisionService.evaluate(transaction, features);
                telemetry.stageDuration("engine", System.nanoTime() - stageStarted);
                modelVersion = fraudDecisionService.modelVersion(transaction);
            }
            ruleVersion = fraudDecisionService.ruleVersion(command.sourceId(), command.txnId());
        } catch (RuntimeException failure) {
            assessment = safeAssessment(RiskLevel.HIGH, "DEGRADED_ENGINE_UNAVAILABLE");
            modelVersion = "unavailable";
            ruleVersion = "unavailable";
        }

        String decisionId = UUID.randomUUID().toString();
        Map<String, String> featureValues = features.asMap().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
        long decisionCost = elapsedMillis(startedAt);
        DecisionRecord decision = new DecisionRecord(decisionId, command.sourceId(), command.txnId(),
                command.correlationId(), command.eventTime(), accountTokenizer.tokenize(command.accountNo()),
                assessment.getLevel().name(), assessment.getAction(), assessment.getFraudScore(),
                assessment.getHitRules(), featureValues, ruleVersion, modelVersion, decisionCost, now);
        List<OutboxMessage> messages = createOutbox(command, decision, now);

        try {
            stageStarted = System.nanoTime();
            commitService.commit(decision, messages);
            telemetry.stageDuration("persistence", System.nanoTime() - stageStarted);
            telemetry.decision(decision.riskLevel(), decision.action(), decision.fraudScore(),
                    decision.hitRules().size(),
                    decision.hitRules().stream().anyMatch(hit -> hit.startsWith("DEGRADED_")));
        } catch (DuplicateKeyException duplicate) {
            return repository.findByTransactionKey(key)
                    .orElseThrow(() -> duplicate).toResult();
        }
        long fullCost = elapsedMillis(startedAt);
        repository.updateCost(decisionId, fullCost);
        return decision.withCostMs(fullCost).toResult();
    }

    private void validateEventTime(Instant eventTime, Instant now) {
        if (eventTime.isBefore(now.minus(maxEventAge))) {
            throw new InvalidEventTimeException("eventTime is older than " + maxEventAge);
        }
        if (eventTime.isAfter(now.plus(maxFutureSkew))) {
            throw new InvalidEventTimeException("eventTime is later than allowed clock skew " + maxFutureSkew);
        }
    }

    private TransactionEvent toDomain(EvaluateTransactionCommand command) {
        TransactionEvent transaction = new TransactionEvent();
        transaction.setTxnId(command.txnId());
        transaction.setSourceId(command.sourceId());
        transaction.setChannel(command.channel());
        transaction.setBizType(command.bizType());
        transaction.setAccountNo(command.accountNo());
        transaction.setCounterpartyAccount(command.counterpartyAccount());
        transaction.setAmount(command.amount());
        transaction.setCurrency(command.currency());
        transaction.setDeviceId(command.deviceId());
        transaction.setIp(command.ip());
        transaction.setEventTime(command.eventTime());
        transaction.setTransactionStatus(command.transactionStatus());
        return transaction;
    }

    private RiskAssessment safeAssessment(RiskLevel level, String reason) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.escalate(level, reason);
        return assessment;
    }

    private List<OutboxMessage> createOutbox(EvaluateTransactionCommand command,
                                              DecisionRecord decision, Instant now) {
        EventMetadataV1 txnMetadata = EventMetadataV1.create(command.correlationId(),
                command.sourceId(), command.txnId(), now);
        TransactionEventV1 transactionEvent = new TransactionEventV1(txnMetadata, command.channel(), command.bizType(),
                command.accountNo(), command.counterpartyAccount(), command.amount(), command.currency(),
                command.deviceId(), command.ip(), command.eventTime(), command.transactionStatus());

        EventMetadataV1 decisionMetadata = EventMetadataV1.create(command.correlationId(),
                command.sourceId(), command.txnId(), now);
        List<RuleHitV1> hits = decision.hitRules().stream()
                .map(code -> new RuleHitV1(code, code, 0, code))
                .toList();
        DecisionEventV1 decisionEvent = new DecisionEventV1(decisionMetadata, decision.decisionId(),
                toContractLevel(decision.riskLevel()), DecisionAction.valueOf(decision.action()),
                decision.fraudScore(), hits, decision.featureSnapshot(), decision.ruleVersion(),
                decision.modelVersion(), decision.costMs());
        return List.of(
                new OutboxMessage(txnMetadata.eventId(), "Transaction", command.txnId(),
                        "transaction.v1", command.accountNo(), json(transactionEvent), now),
                new OutboxMessage(decisionMetadata.eventId(), "Decision", decision.decisionId(),
                        "decision.v1", command.accountNo(), json(decisionEvent), now));
    }

    private DecisionRiskLevel toContractLevel(String riskLevel) {
        return "REJECT".equals(riskLevel) ? DecisionRiskLevel.CRITICAL
                : DecisionRiskLevel.valueOf(riskLevel);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("event serialization failed", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
