package com.lrj.risk.fraud.gateway.decision.application;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import com.lrj.risk.contracts.kernel.TransactionKey;
import com.lrj.risk.fraud.gateway.decision.application.model.RiskDecisionResult;
import org.springframework.stereotype.Component;

/** Collapses concurrent retries in one process while the database unique key protects all instances. */
@Component
public class InFlightDecisionCoordinator {

    private final ConcurrentHashMap<TransactionKey, CompletableFuture<RiskDecisionResult>> inFlight =
            new ConcurrentHashMap<>();

    public RiskDecisionResult execute(TransactionKey key, Supplier<RiskDecisionResult> action) {
        CompletableFuture<RiskDecisionResult> owner = new CompletableFuture<>();
        CompletableFuture<RiskDecisionResult> existing = inFlight.putIfAbsent(key, owner);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException exception) {
                if (exception.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw exception;
            }
        }
        try {
            RiskDecisionResult result = action.get();
            owner.complete(result);
            return result;
        } catch (RuntimeException exception) {
            owner.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, owner);
        }
    }
}
