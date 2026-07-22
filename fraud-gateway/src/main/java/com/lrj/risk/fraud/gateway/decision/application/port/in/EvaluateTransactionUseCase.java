package com.lrj.risk.fraud.gateway.decision.application.port.in;

import com.lrj.risk.fraud.gateway.decision.application.model.EvaluateTransactionCommand;
import com.lrj.risk.fraud.gateway.decision.application.model.RiskDecisionResult;

public interface EvaluateTransactionUseCase {

    RiskDecisionResult evaluate(EvaluateTransactionCommand command);
}
