package com.lrj.risk.contracts.kernel;

/** Canonical idempotency identity shared by API, persistence and event boundaries. */
public record TransactionKey(String sourceId, String txnId) {

    public TransactionKey {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (txnId == null || txnId.isBlank()) {
            throw new IllegalArgumentException("txnId must not be blank");
        }
    }

    public String value() {
        return sourceId + ":" + txnId;
    }
}
