package com.lrj.risk.fraud.gateway.decision.application;

public class InvalidEventTimeException extends RuntimeException {

    public InvalidEventTimeException(String message) {
        super(message);
    }
}
