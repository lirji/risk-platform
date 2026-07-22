package com.lrj.risk.profiling.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OfflineProfileSparkIntegrationTest {

    private static SparkSession spark;

    @BeforeAll
    static void startSpark() {
        spark = SparkSession.builder().master("local[1]").appName("offline-profile-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.sql.session.timeZone", "UTC").getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void calculatesIncrementalProfilesFromCompositeTransactionAndCaseLabelFacts() {
        StructType transactionSchema = new StructType()
                .add("source_id", DataTypes.StringType, false)
                .add("txn_id", DataTypes.StringType, false)
                .add("customer_id", DataTypes.StringType, false)
                .add("counterparty_id", DataTypes.StringType, true)
                .add("amount_minor", DataTypes.LongType, false)
                .add("event_time", DataTypes.TimestampType, false);
        Dataset<Row> transactions = spark.createDataFrame(List.of(
                RowFactory.create("MOBILE", "t1", "cust-1", "cp-1", 100L,
                        Timestamp.from(Instant.parse("2026-07-22T01:00:00Z"))),
                RowFactory.create("MOBILE", "t2", "cust-1", "cp-2", 200L,
                        Timestamp.from(Instant.parse("2026-07-21T12:00:00Z")))), transactionSchema);
        StructType labelSchema = new StructType()
                .add("source_id", DataTypes.StringType, false)
                .add("txn_id", DataTypes.StringType, false)
                .add("label", DataTypes.StringType, false)
                .add("created_at", DataTypes.TimestampType, false);
        Dataset<Row> labels = spark.createDataFrame(List.of(
                RowFactory.create("MOBILE", "t1", "FRAUD",
                        Timestamp.from(Instant.parse("2026-07-22T02:00:00Z"))),
                RowFactory.create("MOBILE", "t2", "NORMAL",
                        Timestamp.from(Instant.parse("2026-07-21T13:00:00Z")))), labelSchema);

        Row profile = OfflineProfileJob.calculateProfiles(transactions, labels,
                        "2026-07-21T00:00:00Z", "2026-07-22T12:00:00Z", "UTC")
                .profiles().head();

        assertEquals("cust-1", profile.getAs("customer_id"));
        assertEquals(300L, ((Number) profile.getAs("amount_90d")).longValue());
        assertEquals(2L, ((Number) profile.getAs("txn_cnt_90d")).longValue());
        assertEquals(2L, ((Number) profile.getAs("unique_counterparty_90d")).longValue());
        assertEquals(0.5d, ((Number) profile.getAs("night_ratio")).doubleValue(), 0.0001d);
        assertEquals(1L, ((Number) profile.getAs("fraud_count_90d")).longValue());
    }
}
