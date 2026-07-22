package com.lrj.risk.profiling.offline.domain;

public record CustomerProfile(String customerId, long amount90d, long transactionCount90d,
                              long uniqueCounterparties, long daysSinceLastTransaction,
                              double nightRatio, long fraudCount) {
}
