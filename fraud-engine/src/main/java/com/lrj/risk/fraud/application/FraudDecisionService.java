package com.lrj.risk.fraud.application;

import com.lrj.risk.feature.domain.FeatureSnapshot;
import com.lrj.risk.fraud.application.port.out.ModelScoringPort;
import com.lrj.risk.fraud.application.port.out.RuleEvaluationPort;
import com.lrj.risk.fraud.application.port.out.RuleSetBindingPort;
import com.lrj.risk.fraud.domain.model.RiskAssessment;
import com.lrj.risk.fraud.domain.model.TransactionEvent;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application use case coordinating pure input with model, binding and rule ports. */
@Service
public class FraudDecisionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDecisionService.class);

    private final RuleEvaluationPort ruleEvaluator;
    private final RuleSetBindingPort ruleSetBinding;
    private final ModelScoringPort modelScorer;

    public FraudDecisionService(RuleEvaluationPort ruleEvaluator, RuleSetBindingPort ruleSetBinding,
                                ModelScoringPort modelScorer) {
        this.ruleEvaluator = ruleEvaluator;
        this.ruleSetBinding = ruleSetBinding;
        this.modelScorer = modelScorer;
    }

    public RiskAssessment evaluate(TransactionEvent transaction, FeatureSnapshot features) {
        double score = modelScorer.score(transaction, features);
        var selected = ruleSetBinding.resolve(transaction.getSourceId(), transaction.getTxnId());
        RiskAssessment decision = ruleEvaluator.evaluate(transaction, features, score,
                selected.ruleSets(), selected.version());
        ruleSetBinding.shadow(transaction.getSourceId()).ifPresent(shadow -> {
            RiskAssessment shadowDecision = ruleEvaluator.evaluate(transaction, features, score,
                    shadow.ruleSets(), shadow.version());
            if (shadowDecision.getLevel() != decision.getLevel()
                    || !shadowDecision.getHitRules().equals(decision.getHitRules())) {
                log.info("rule_shadow_diff sourceId={} txnId={} activeVersion={} shadowVersion={} activeLevel={} shadowLevel={}",
                        transaction.getSourceId(), transaction.getTxnId(), selected.version(), shadow.version(),
                        decision.getLevel(), shadowDecision.getLevel());
            }
        });
        return decision;
    }

    public String ruleVersion(String sourceId, String transactionId) {
        return ruleSetBinding.resolve(sourceId, transactionId).version();
    }

    public String modelVersion(TransactionEvent transaction) {
        return modelScorer.activeVersion(transaction);
    }
}
