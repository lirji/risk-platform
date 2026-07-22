package com.lrj.risk.cep;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.v1.DecisionRiskLevel;
import com.lrj.risk.contracts.v1.EventMetadataV1;
import com.lrj.risk.contracts.v1.RiskSignalV1;
import com.lrj.risk.contracts.v1.TransactionEventV1;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.cep.CEP;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public final class FraudCepJob {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private FraudCepJob() {
    }

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.enableCheckpointing(10_000);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrap).setTopics("transaction.v1").setGroupId("fraud-cep-v1")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema()).build();
        WatermarkStrategy<TransactionEventV1> watermark = WatermarkStrategy
                .<TransactionEventV1>forBoundedOutOfOrderness(Duration.ofMinutes(2))
                .withTimestampAssigner((event, previous) -> event.eventTime().toEpochMilli());
        DataStream<TransactionEventV1> transactions = environment
                .fromSource(source, WatermarkStrategy.noWatermarks(), "transaction-v1")
                .map(FraudCepJob::parse)
                .assignTimestampsAndWatermarks(watermark);

        DataStream<RiskSignalV1> probing = CEP.pattern(transactions.keyBy(TransactionEventV1::accountNo),
                        CepPatternDefinitions.probeThenLarge())
                .select(FraudCepJob::probingSignal);
        DataStream<RiskSignalV1> failed = CEP.pattern(transactions.keyBy(TransactionEventV1::accountNo),
                        CepPatternDefinitions.failedThenSuccess())
                .select(FraudCepJob::failedSignal);

        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrap)
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("risk-signal.v1")
                        .setValueSerializationSchema(new SimpleStringSchema()).build())
                .build();
        probing.union(failed).map(FraudCepJob::json).sinkTo(sink).name("risk-signal-v1");
        environment.execute("risk-platform-fraud-cep-v1");
    }

    private static RiskSignalV1 probingSignal(Map<String, List<TransactionEventV1>> pattern) {
        TransactionEventV1 probe = pattern.get("probe").getFirst();
        TransactionEventV1 large = pattern.get("large").getFirst();
        return signal("PROBE_THEN_LARGE", DecisionRiskLevel.HIGH, large,
                List.of(probe.metadata().eventId(), large.metadata().eventId()),
                Map.of("probeAmount", Long.toString(probe.amountMinor()),
                        "largeAmount", Long.toString(large.amountMinor())));
    }

    private static RiskSignalV1 failedSignal(Map<String, List<TransactionEventV1>> pattern) {
        List<TransactionEventV1> failures = pattern.get("failed");
        TransactionEventV1 success = pattern.get("success").getFirst();
        List<String> eventIds = java.util.stream.Stream.concat(failures.stream(), java.util.stream.Stream.of(success))
                .map(event -> event.metadata().eventId()).toList();
        return signal("FAILED_THEN_SUCCESS", DecisionRiskLevel.HIGH, success, eventIds,
                Map.of("failureCount", Integer.toString(failures.size())));
    }

    private static RiskSignalV1 signal(String type, DecisionRiskLevel level, TransactionEventV1 terminal,
                                       List<String> eventIds, Map<String, String> attributes) {
        Instant now = Instant.now();
        return new RiskSignalV1(EventMetadataV1.create(terminal.metadata().correlationId(),
                terminal.metadata().sourceId(), terminal.metadata().txnId(), now),
                UUID.randomUUID().toString(), type, terminal.accountNo(), level, eventIds, now, attributes);
    }

    private static TransactionEventV1 parse(String json) throws Exception {
        return MAPPER.readValue(json, TransactionEventV1.class);
    }

    private static String json(RiskSignalV1 signal) throws Exception {
        return MAPPER.writeValueAsString(signal);
    }
}
