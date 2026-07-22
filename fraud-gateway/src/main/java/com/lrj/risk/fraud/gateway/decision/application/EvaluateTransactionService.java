package com.lrj.risk.fraud.gateway.decision.application;

import com.lrj.risk.contracts.kernel.TransactionKey;
import com.lrj.risk.fraud.gateway.decision.application.model.EvaluateTransactionCommand;
import com.lrj.risk.fraud.gateway.decision.application.model.RiskDecisionResult;
import com.lrj.risk.fraud.gateway.decision.application.port.in.EvaluateTransactionUseCase;
import org.springframework.stereotype.Service;

@Service
public class EvaluateTransactionService implements EvaluateTransactionUseCase {

    private final InFlightDecisionCoordinator coordinator;
    private final TransactionalDecisionService transactionalService;

    public EvaluateTransactionService(InFlightDecisionCoordinator coordinator,
                                      TransactionalDecisionService transactionalService) {
        this.coordinator = coordinator;
        this.transactionalService = transactionalService;
    }

    @Override
    public RiskDecisionResult evaluate(EvaluateTransactionCommand command) {
        TransactionKey key = new TransactionKey(command.sourceId(), command.txnId());
        return coordinator.execute(key, () -> transactionalService.evaluate(command, key));
    }
}
