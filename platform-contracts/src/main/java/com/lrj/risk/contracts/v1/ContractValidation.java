package com.lrj.risk.contracts.v1;

import java.util.Objects;

final class ContractValidation {

    private ContractValidation() {
    }

    static <T> T required(T value, String field) {
        return Objects.requireNonNull(value, field + " is required");
    }

    static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
