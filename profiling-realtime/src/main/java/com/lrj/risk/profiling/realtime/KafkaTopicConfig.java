package com.lrj.risk.profiling.realtime;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_TRANSACTION_V1 = "transaction.v1";
    public static final String TOPIC_TRANSACTION_DLT = "transaction.v1.DLT";

    @Bean
    NewTopic transactionV1Topic() {
        return TopicBuilder.name(TOPIC_TRANSACTION_V1).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic transactionDltTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTION_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, failure) -> new TopicPartition(TOPIC_TRANSACTION_DLT, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500, 3));
    }
}
