package com.lrj.risk.contracts.v1;

import java.time.Instant;
import java.util.UUID;

/** Every integration event carries stable identity, correlation and schema metadata. */
public record EventMetadataV1(
        String eventId,
        String correlationId,
        String sourceId,
        String txnId,
        Instant occurredAt,
        int schemaVersion) {

    public static final int VERSION = 1;

    public EventMetadataV1 {
        ContractValidation.text(eventId, "eventId");
        ContractValidation.text(correlationId, "correlationId");
        ContractValidation.text(sourceId, "sourceId");
        ContractValidation.text(txnId, "txnId");
        ContractValidation.required(occurredAt, "occurredAt");
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("schemaVersion must be " + VERSION);
        }
    }

    public static EventMetadataV1 create(String correlationId, String sourceId, String txnId, Instant now) {
        return new EventMetadataV1(UUID.randomUUID().toString(), correlationId, sourceId, txnId, now, VERSION);
    }
}
