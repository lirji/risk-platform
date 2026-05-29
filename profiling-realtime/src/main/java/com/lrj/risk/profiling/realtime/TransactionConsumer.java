package com.lrj.risk.profiling.realtime;

import com.lrj.risk.common.event.TransactionMessage;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 消费 {@code txn-events} 交易流, 委托 {@link RealtimeFeatureService} 算实时特征。
 */
@Component
public class TransactionConsumer {

    private final RealtimeFeatureService featureService;

    public TransactionConsumer(RealtimeFeatureService featureService) {
        this.featureService = featureService;
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_TXN_EVENTS, groupId = "realtime-feature")
    public void onMessage(TransactionMessage msg) {
        featureService.onTransaction(msg);
    }
}
