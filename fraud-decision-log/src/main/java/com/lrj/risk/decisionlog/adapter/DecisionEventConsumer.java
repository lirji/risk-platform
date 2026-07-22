package com.lrj.risk.decisionlog.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.v1.DecisionEventV1;
import com.lrj.risk.decisionlog.application.DecisionIndexPort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DecisionEventConsumer {

    private final ObjectMapper mapper;
    private final DecisionIndexPort index;

    public DecisionEventConsumer(ObjectMapper mapper, DecisionIndexPort index) {
        this.mapper = mapper;
        this.index = index;
    }

    @KafkaListener(topics = "decision.v1", groupId = "fraud-decision-log-v1")
    public void consume(String payload) {
        try {
            index.index(mapper.readValue(payload, DecisionEventV1.class));
        } catch (Exception exception) {
            throw new IllegalArgumentException("decision.v1 projection failed", exception);
        }
    }
}
