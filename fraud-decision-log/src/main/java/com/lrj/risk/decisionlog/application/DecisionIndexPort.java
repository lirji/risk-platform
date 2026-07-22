package com.lrj.risk.decisionlog.application;

import java.util.Map;

import com.lrj.risk.contracts.v1.DecisionEventV1;

public interface DecisionIndexPort {
    void index(DecisionEventV1 event);
    Map<String, Object> search(String riskLevel, int page, int size);
    Map<String, Object> detail(String decisionId);
}
