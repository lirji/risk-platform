package com.lrj.risk.contracts.v1;

import java.time.Instant;
import java.util.Map;

public record ApiErrorV1(
        ErrorCode code,
        String message,
        String traceId,
        Instant timestamp,
        Map<String, String> fieldErrors) {

    public ApiErrorV1 {
        ContractValidation.required(code, "code");
        ContractValidation.text(message, "message");
        ContractValidation.text(traceId, "traceId");
        ContractValidation.required(timestamp, "timestamp");
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }
}
