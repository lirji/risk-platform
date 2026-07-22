package com.lrj.risk.fraud.gateway.api;

import java.io.IOException;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Measures the complete server-side request, including serialization and persistence. */
@Component
public class DecisionLatencyFilter extends OncePerRequestFilter {

    private final MeterRegistry registry;

    public DecisionLatencyFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("X-Correlation-Id");
        String traceId = header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
        request.setAttribute("risk.traceId", traceId);
        response.setHeader("X-Correlation-Id", traceId);
        Timer.Sample sample = Timer.start(registry);
        try {
            chain.doFilter(request, response);
        } finally {
            sample.stop(registry.timer("risk.decision.http.duration", "method", request.getMethod(),
                    "status", Integer.toString(response.getStatus())));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/risk/");
    }
}
