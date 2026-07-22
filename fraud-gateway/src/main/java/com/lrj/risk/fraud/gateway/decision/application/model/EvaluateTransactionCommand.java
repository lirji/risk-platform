package com.lrj.risk.fraud.gateway.decision.application.model;

import java.time.Instant;
import com.lrj.risk.contracts.v1.TransactionStatus;

public record EvaluateTransactionCommand(
        String sourceId,
        String txnId,
        String channel,
        String bizType,
        String accountNo,
        String counterpartyAccount,
        long amount,
        String currency,
        String deviceId,
        String ip,
        Instant eventTime,
        TransactionStatus transactionStatus,
        String correlationId) {
}
