package com.lrj.risk.profiling.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.v1.TransactionEventV1;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionConsumer {

    private final RealtimeFeatureService featureService;
    private final ObjectMapper objectMapper;

    public TransactionConsumer(RealtimeFeatureService featureService, ObjectMapper objectMapper) {
        this.featureService = featureService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_TRANSACTION_V1, groupId = "realtime-feature-v1")
    public void onMessage(String payload) {
        try {
            featureService.onTransaction(objectMapper.readValue(payload, TransactionEventV1.class));
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid transaction.v1 event", exception);
        }
    }
}
