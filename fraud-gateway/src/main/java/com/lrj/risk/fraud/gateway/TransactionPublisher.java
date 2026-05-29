package com.lrj.risk.fraud.gateway;

import com.lrj.risk.common.event.TransactionMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 交易事件发布器: 决策完成后把交易异步发到 Kafka (§3.6 非关键路径异步化)。
 *
 * <p>按账号分区 (key=accountNo), 保证同账号有序又均匀分散 (§2.2.1 防倾斜)。
 * 发送失败只记日志、不抛出 —— 数据流故障绝不影响银行侧同步决策。
 */
@Component
public class TransactionPublisher {

    public static final String TOPIC = "txn-events";
    private static final Logger log = LoggerFactory.getLogger(TransactionPublisher.class);

    private final KafkaTemplate<String, TransactionMessage> kafka;

    public TransactionPublisher(KafkaTemplate<String, TransactionMessage> kafka) {
        this.kafka = kafka;
    }

    public void publishAsync(TransactionMessage msg) {
        try {
            kafka.send(TOPIC, msg.getAccountNo(), msg)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("交易事件发布失败(忽略, 不影响决策) accountNo={}, cause={}",
                                    msg.getAccountNo(), ex.toString());
                        }
                    });
        } catch (Exception e) {
            // 连 enqueue 都失败 (如序列化异常) 也不能影响主链路
            log.warn("交易事件入队失败(忽略) accountNo={}, cause={}", msg.getAccountNo(), e.toString());
        }
    }
}
