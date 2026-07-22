package com.lrj.risk.fraud.gateway.decision.application;

import java.util.List;

import com.lrj.risk.fraud.gateway.decision.application.port.out.DecisionRepository;
import com.lrj.risk.fraud.gateway.decision.domain.DecisionRecord;
import com.lrj.risk.fraud.gateway.decision.domain.OutboxMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits the immutable decision, case and outbox in one transaction. */
@Service
public class DecisionCommitService {
    private final DecisionRepository repository;

    public DecisionCommitService(DecisionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void commit(DecisionRecord decision, List<OutboxMessage> messages) {
        repository.save(decision, messages);
    }
}
