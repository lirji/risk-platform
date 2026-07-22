package com.lrj.risk.fraud.gateway;

import java.util.UUID;

import com.lrj.risk.fraud.gateway.decision.application.model.EvaluateTransactionCommand;
import com.lrj.risk.fraud.gateway.decision.application.model.RiskDecisionResult;
import com.lrj.risk.fraud.gateway.decision.application.port.in.EvaluateTransactionUseCase;
import com.lrj.risk.fraud.gateway.dto.RiskRequest;
import com.lrj.risk.fraud.gateway.dto.RiskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter for the synchronous decision use case. */
@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

    private final EvaluateTransactionUseCase useCase;

    public RiskController(EvaluateTransactionUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/evaluations")
    public RiskResponse evaluate(
            @Valid @RequestBody RiskRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationHeader) {
        String correlationId = correlationHeader == null || correlationHeader.isBlank()
                ? UUID.randomUUID().toString() : correlationHeader;
        EvaluateTransactionCommand command = new EvaluateTransactionCommand(
                request.sourceId(), request.txnId(), request.channel(), request.bizType(),
                request.accountNo(), request.counterpartyAccount(), request.amount(), request.currency(),
                request.deviceId(), request.ip(), request.eventTime(), request.transactionStatus(), correlationId);
        return toResponse(useCase.evaluate(command));
    }

    private RiskResponse toResponse(RiskDecisionResult result) {
        return new RiskResponse(result.decisionId(), result.txnId(), result.riskLevel(), result.action(),
                result.fraudScore(), result.hitRules(), result.ruleVersion(), result.modelVersion(),
                result.costMs());
    }
}
