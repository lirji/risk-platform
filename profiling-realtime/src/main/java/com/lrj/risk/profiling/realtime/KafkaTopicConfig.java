package com.lrj.risk.profiling.realtime;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 交易流 topic 定义。3 分区演示按账号 key 的并行消费与防倾斜 (§2.2.1)。
 */
@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_TXN_EVENTS = "txn-events";

    @Bean
    public NewTopic txnEventsTopic() {
        return TopicBuilder.name(TOPIC_TXN_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
