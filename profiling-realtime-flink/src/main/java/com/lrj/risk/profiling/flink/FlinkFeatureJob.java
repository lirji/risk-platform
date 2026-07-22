package com.lrj.risk.profiling.flink;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.v1.TransactionEventV1;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public final class FlinkFeatureJob {

    static final String TOPIC = "transaction.v1";
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private FlinkFeatureJob() {
    }

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
        String checkpointUri = System.getenv().getOrDefault("FLINK_CHECKPOINT_URI",
                "file:///tmp/risk-platform-flink-checkpoints");
        String zoneId = System.getenv().getOrDefault("FEATURE_TIME_ZONE", "UTC");

        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();
        Configuration faultTolerance = new Configuration();
        faultTolerance.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, checkpointUri);
        faultTolerance.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        faultTolerance.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, 3);
        faultTolerance.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, Duration.ofSeconds(5));
        environment.configure(faultTolerance);
        environment.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrap)
                .setTopics(TOPIC)
                .setGroupId("flink-feature-v1")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        WatermarkStrategy<TransactionEventV1> watermarks = WatermarkStrategy
                .<TransactionEventV1>forBoundedOutOfOrderness(Duration.ofMinutes(2))
                .withTimestampAssigner((event, previousTimestamp) -> event.eventTime().toEpochMilli())
                .withIdleness(Duration.ofMinutes(1));

        environment.fromSource(source, WatermarkStrategy.noWatermarks(), "transaction-v1-source")
                .map(FlinkFeatureJob::parse)
                .filter(event -> event != null && !event.accountNo().isBlank())
                .assignTimestampsAndWatermarks(watermarks)
                .keyBy(TransactionEventV1::accountNo)
                .process(new FeatureAggregator(zoneId))
                .name("feature-domain-aggregator")
                .addSink(new RedisFeatureSink())
                .name("redis-feature-projection");

        environment.execute("risk-platform-realtime-feature-v1");
    }

    static TransactionEventV1 parse(String json) {
        try {
            return MAPPER.readValue(json, TransactionEventV1.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid transaction.v1 event", exception);
        }
    }
}
