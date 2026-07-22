package com.lrj.risk.fraud.gateway.api;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.lrj.risk.contracts.v1.ApiErrorV1;
import com.lrj.risk.contracts.v1.ErrorCode;
import com.lrj.risk.fraud.gateway.decision.application.InvalidEventTimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorV1> validation(MethodArgumentNotValidException exception,
                                          HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "request validation failed", fields, request);
    }

    @ExceptionHandler({InvalidEventTimeException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiErrorV1> malformed(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiErrorV1> database(DataAccessException exception, HttpServletRequest request) {
        log.error("decision persistence unavailable traceId={}", traceId(request), exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE,
                "decision persistence is temporarily unavailable", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorV1> unexpected(Exception exception, HttpServletRequest request) {
        log.error("unexpected decision error traceId={}", traceId(request), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "unexpected internal error", Map.of(), request);
    }

    private ResponseEntity<ApiErrorV1> error(HttpStatus status, ErrorCode code, String message,
                                             Map<String, String> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorV1(code,
                message == null || message.isBlank() ? code.name() : message,
                traceId(request), clock.instant(), fields));
    }

    private String traceId(HttpServletRequest request) {
        Object existing = request.getAttribute("risk.traceId");
        if (existing != null) {
            return existing.toString();
        }
        String header = request.getHeader("X-Correlation-Id");
        String traceId = header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
        request.setAttribute("risk.traceId", traceId);
        return traceId;
    }
}
