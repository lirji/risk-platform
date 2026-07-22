package com.lrj.risk.contracts.v1;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RiskSignalV1(
        EventMetadataV1 metadata,
        String signalId,
        String signalType,
        String accountNo,
        DecisionRiskLevel severity,
        List<String> relatedEventIds,
        Instant detectedAt,
        Map<String, String> attributes) {

    public RiskSignalV1 {
        ContractValidation.required(metadata, "metadata");
        ContractValidation.text(signalId, "signalId");
        ContractValidation.text(signalType, "signalType");
        ContractValidation.text(accountNo, "accountNo");
        ContractValidation.required(severity, "severity");
        relatedEventIds = List.copyOf(relatedEventIds);
        ContractValidation.required(detectedAt, "detectedAt");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
