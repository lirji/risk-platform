package com.lrj.risk.decisionlog.config;

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
public class KafkaReliabilityConfig {

    @Bean NewTopic decisionTopic() { return TopicBuilder.name("decision.v1").partitions(3).replicas(1).build(); }
    @Bean NewTopic decisionDltTopic() { return TopicBuilder.name("decision.v1.DLT").partitions(3).replicas(1).build(); }

    @Bean
    DefaultErrorHandler decisionErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, failure) -> new TopicPartition("decision.v1.DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500, 3));
    }
}
