package com.lrj.risk.admin.decisions.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface DecisionQuery {
    Page<DecisionView> search(String riskLevel, String transactionId, int page, int size);

    Map<String, Object> detail(String decisionId);

    record Page<T>(List<T> content, long total, int page, int size) { }

    record DecisionView(String decisionId, String sourceId, String txnId, Instant eventTime,
                        String riskLevel, String action, double fraudScore, String hitRulesJson,
                        String ruleVersion, String modelVersion, long costMs, Instant createdAt) { }
}
