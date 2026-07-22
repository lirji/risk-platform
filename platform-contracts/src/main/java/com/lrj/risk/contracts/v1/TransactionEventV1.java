package com.lrj.risk.contracts.v1;

import java.time.Instant;

/** Versioned transaction fact published after a decision is durably recorded. */
public record TransactionEventV1(
        EventMetadataV1 metadata,
        String channel,
        String bizType,
        String accountNo,
        String counterpartyAccount,
        long amountMinor,
        String currency,
        String deviceId,
        String ip,
        Instant eventTime,
        TransactionStatus transactionStatus) {

    public TransactionEventV1(EventMetadataV1 metadata, String channel, String bizType,
                              String accountNo, String counterpartyAccount, long amountMinor,
                              String currency, String deviceId, String ip, Instant eventTime) {
        this(metadata, channel, bizType, accountNo, counterpartyAccount, amountMinor, currency,
                deviceId, ip, eventTime, TransactionStatus.UNKNOWN);
    }

    public TransactionEventV1 {
        ContractValidation.required(metadata, "metadata");
        ContractValidation.text(channel, "channel");
        ContractValidation.text(bizType, "bizType");
        ContractValidation.text(accountNo, "accountNo");
        ContractValidation.text(currency, "currency");
        ContractValidation.required(eventTime, "eventTime");
        transactionStatus = transactionStatus == null ? TransactionStatus.UNKNOWN : transactionStatus;
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
    }
}
