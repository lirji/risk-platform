package com.lrj.risk.fraud.engine;

import java.util.List;

import com.lrj.risk.feature.FeatureSnapshot;
import com.lrj.risk.fraud.engine.model.RiskAssessment;
import com.lrj.risk.fraud.engine.model.TransactionEvent;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.Agenda;
import org.springframework.stereotype.Service;

/**
 * 反欺诈规则引擎服务: 一笔交易 + 预取特征 → 风险评估结果。
 *
 * <p>关键约束 (PLAN §3.6): 特征由调用方(gateway)预先取齐传入, 引擎内零 IO;
 * 按 sourceId 解析绑定的规则集, 只激活对应 agenda-group; fireAllRules 带硬上限护栏 (参考 drools-demo Step 14)。
 * KieSession 线程不安全, 每次请求新建 + dispose。
 */
@Service
public class FraudEngineService {

    /** 失控护栏: 单次决策最多触发的规则数上限。 */
    private static final int MAX_FIRES = 1000;

    private final KieContainer kieContainer;
    private final SourceRuleSetBinding binding;

    public FraudEngineService(KieContainer fraudKieContainer, SourceRuleSetBinding binding) {
        this.kieContainer = fraudKieContainer;
        this.binding = binding;
    }

    public RiskAssessment evaluate(TransactionEvent txn, FeatureSnapshot features) {
        KieSession session = kieContainer.newKieSession("fraudSession");
        try {
            RiskAssessment assessment = new RiskAssessment();
            session.insert(txn);
            session.insert(features);
            session.insert(assessment);

            // 按来源绑定激活规则集; agenda 是 LIFO 栈, 逆序 setFocus 让列表首位先触发
            List<String> ruleSets = binding.resolve(txn.getSourceId());
            Agenda agenda = session.getAgenda();
            for (int i = ruleSets.size() - 1; i >= 0; i--) {
                agenda.getAgendaGroup(ruleSets.get(i)).setFocus();
            }

            session.fireAllRules(MAX_FIRES);
            return assessment;
        } finally {
            session.dispose();
        }
    }
}
