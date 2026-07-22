package com.lrj.risk.profiling.offline.domain;

import java.time.Instant;

public record TransactionFact(String customerId, long amountMinor, String counterpartyId,
                              Instant eventTime, boolean fraud) {
}
