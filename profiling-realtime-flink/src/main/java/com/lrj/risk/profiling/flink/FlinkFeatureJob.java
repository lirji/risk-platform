package com.lrj.risk.profiling.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.common.event.TransactionMessage;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * 实时特征计算 (Flink 版) —— profiling-realtime 轻量消费者的 Flink 平替 (PLAN §2.2)。
 *
 * <p>DataStream: KafkaSource(txn-events) → 解析 → keyBy(账号) → 键控状态聚合
 * (当日累计/笔数 + 5 分钟滑窗计数) → 写 Redis feature:{账号}。
 * 用 {@code StreamExecutionEnvironment.getExecutionEnvironment()} 嵌入式本地 MiniCluster 运行。
 *
 * <p>注意: 与 profiling-realtime 二选一 (不同 consumer group, 同时跑会重复写特征)。
 */
public class FlinkFeatureJob {

    static final String TOPIC = "txn-events";

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrap)
                .setTopics(TOPIC)
                .setGroupId("flink-feature")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> raw = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-txn");

        raw.map(FlinkFeatureJob::parse)
                .filter(m -> m != null && m.getAccountNo() != null && !m.getAccountNo().isBlank())
                .keyBy(TransactionMessage::getAccountNo)
                .process(new FeatureAggregator())
                .name("feature-aggregator");

        env.execute("flink-realtime-feature");
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static TransactionMessage parse(String json) {
        try {
            return MAPPER.readValue(json, TransactionMessage.class);
        } catch (Exception e) {
            return null;
        }
    }
}
