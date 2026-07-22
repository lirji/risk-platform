package com.lrj.risk.fraud.gateway.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.lrj.risk.contracts.v1.TransactionStatus;

/** Bank-facing request. Amount is expressed in minor currency units. */
public record RiskRequest(
        @NotBlank @Size(max = 64) String sourceId,
        @NotBlank @Size(max = 128) String txnId,
        @NotBlank @Pattern(regexp = "MOBILE|WEB|ATM|API|BRANCH") String channel,
        @NotBlank @Pattern(regexp = "TRANSFER|REMITTANCE|PAYMENT|WITHDRAWAL") String bizType,
        @NotBlank @Size(max = 128) String accountNo,
        @Size(max = 128) String counterpartyAccount,
        @Positive long amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 128) String deviceId,
        @Size(max = 64) String ip,
        @NotNull Instant eventTime,
        TransactionStatus transactionStatus) {
}
