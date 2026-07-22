package com.lrj.risk.fraud.engine;

import java.util.List;

import com.lrj.risk.feature.domain.FeatureSnapshot;
import com.lrj.risk.fraud.application.port.out.RuleEvaluationPort;
import com.lrj.risk.fraud.domain.model.RiskAssessment;
import com.lrj.risk.fraud.domain.model.TransactionEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.Agenda;
import org.springframework.stereotype.Component;

/** Drools output adapter. A session is created per decision because KieSession is not thread-safe. */
@Component
public class DroolsRuleEvaluator implements RuleEvaluationPort {

    private static final int MAX_FIRES = 1_000;
    private final KieContainerHolder containerHolder;

    public DroolsRuleEvaluator(KieContainerHolder containerHolder) {
        this.containerHolder = containerHolder;
    }

    @Override
    public RiskAssessment evaluate(TransactionEvent transaction, FeatureSnapshot features,
                                   double fraudScore, List<String> ruleSets, String ruleVersion) {
        KieSession session = containerHolder.get(transaction.getSourceId(), ruleVersion)
                .newKieSession("fraudSession");
        try {
            RiskAssessment assessment = new RiskAssessment();
            assessment.setFraudScore(fraudScore);
            session.insert(transaction);
            session.insert(features);
            session.insert(assessment);
            Agenda agenda = session.getAgenda();
            for (int index = ruleSets.size() - 1; index >= 0; index--) {
                agenda.getAgendaGroup(ruleSets.get(index)).setFocus();
            }
            session.fireAllRules(MAX_FIRES);
            return assessment;
        } finally {
            session.dispose();
        }
    }
}
