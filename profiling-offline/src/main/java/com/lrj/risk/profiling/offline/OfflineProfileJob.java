package com.lrj.risk.profiling.offline;

import static org.apache.spark.sql.functions.*;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import java.time.Instant;
import java.util.UUID;

/** Incremental profile job sourced from durable transaction facts and analyst case labels. */
public final class OfflineProfileJob {

    private OfflineProfileJob() { }

    public static void main(String[] args) {
        String metastore = System.getenv().getOrDefault("HIVE_METASTORE_URIS", "thrift://localhost:9083");
        String endpoint = System.getenv().getOrDefault("S3_ENDPOINT", "http://localhost:9000");
        String watermark = System.getenv().getOrDefault("PROFILE_WATERMARK", "1970-01-01T00:00:00Z");
        String asOf = System.getenv().getOrDefault("PROFILE_AS_OF", Instant.now().toString());
        String zone = System.getenv().getOrDefault("FEATURE_TIME_ZONE", "UTC");
        SparkSession spark = SparkSession.builder().appName("risk-offline-profile")
                .master(System.getenv().getOrDefault("SPARK_MASTER", "local[*]"))
                .config("spark.ui.enabled", "false").config("spark.sql.session.timeZone", "UTC")
                .config("spark.sql.catalogImplementation", "hive")
                .config("spark.hadoop.hive.metastore.uris", metastore)
                .config("spark.sql.warehouse.dir", "s3a://warehouse")
                .config("spark.hadoop.fs.s3a.endpoint", endpoint)
                .config("spark.hadoop.fs.s3a.access.key", requiredEnv("S3_ACCESS_KEY"))
                .config("spark.hadoop.fs.s3a.secret.key", requiredEnv("S3_SECRET_KEY"))
                .config("spark.hadoop.fs.s3a.path.style.access", "true")
                .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
                .enableHiveSupport().getOrCreate();
        try {
            Dataset<Row> allTransactions = spark.table("risk_dw.dwd_transaction_fact");
            Dataset<Row> rawLabels = spark.table("risk_dw.fact_case_label");
            ProfileBatch batch = calculateProfiles(allTransactions, rawLabels, watermark, asOf, zone);
            Dataset<Row> profiles = batch.profiles();
            mergeProfiles(spark, profiles, batch.affectedCustomers());
            if (Boolean.parseBoolean(System.getenv().getOrDefault("PROFILE_SERVING_ENABLED", "false"))) {
                profiles.toJSON().foreachPartition(new ProfileProjectionWriter(
                        System.getenv().getOrDefault("ES_URL", "http://localhost:9200"),
                        System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                        Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"))));
            }
        } finally {
            spark.stop();
        }
    }

    static ProfileBatch calculateProfiles(Dataset<Row> allTransactions, Dataset<Row> rawLabels,
                                          String watermark, String asOf, String zone) {
        Dataset<Row> labels = rawLabels
                .select(col("source_id"), col("txn_id"),
                        when(col("label").equalTo("FRAUD"), 1).otherwise(0).alias("fraud_label"));
        long windowSeconds = 90L * 24 * 60 * 60;
        Column eventSeconds = col("event_time").cast("long");
        Column watermarkSeconds = unix_timestamp(to_timestamp(lit(watermark)));
        Column windowStart = unix_timestamp(to_timestamp(lit(asOf))).minus(lit(windowSeconds));
        Dataset<Row> transactionAffectedCustomers = allTransactions.filter(
                        eventSeconds.geq(watermarkSeconds)
                                .or(eventSeconds.lt(windowStart).and(
                                        eventSeconds.geq(watermarkSeconds.minus(lit(windowSeconds))))))
                .select("customer_id").distinct();
        Dataset<Row> labelAffectedCustomers = allTransactions
                .join(rawLabels.filter(col("created_at").geq(to_timestamp(lit(watermark))))
                                .select("source_id", "txn_id"),
                        new String[] {"source_id", "txn_id"}, "inner")
                .select("customer_id").distinct();
        Dataset<Row> affectedCustomers = transactionAffectedCustomers
                .unionByName(labelAffectedCustomers).distinct();
        Dataset<Row> transactions = allTransactions
                .filter(eventSeconds.geq(windowStart).and(eventSeconds.leq(
                        unix_timestamp(to_timestamp(lit(asOf))))))
                .join(affectedCustomers, new String[] {"customer_id"}, "inner");
        Dataset<Row> facts = transactions.join(labels, new String[]{"source_id", "txn_id"}, "left")
                .na().fill(0, new String[]{"fraud_label"});
        Dataset<Row> aggregates = facts.groupBy("customer_id").agg(
                sum("amount_minor").alias("amount_90d"), count(lit(1)).alias("txn_cnt_90d"),
                approx_count_distinct("counterparty_id").alias("unique_counterparty_90d"),
                datediff(to_date(lit(asOf)), max(to_date(col("event_time")))).alias("recency_days"),
                avg(when(hour(from_utc_timestamp(col("event_time"), zone)).lt(6)
                        .or(hour(from_utc_timestamp(col("event_time"), zone)).geq(23)), 1).otherwise(0))
                        .alias("night_ratio"),
                sum("fraud_label").alias("fraud_count_90d"), max("event_time").alias("profile_as_of"));
        Dataset<Row> profiles = affectedCustomers.join(aggregates,
                        new String[] {"customer_id"}, "left")
                .select(col("customer_id"),
                        coalesce(col("amount_90d"), lit(0L)).alias("amount_90d"),
                        coalesce(col("txn_cnt_90d"), lit(0L)).alias("txn_cnt_90d"),
                        coalesce(col("unique_counterparty_90d"), lit(0L)).alias("unique_counterparty_90d"),
                        coalesce(col("recency_days"), lit(91)).alias("recency_days"),
                        coalesce(col("night_ratio"), lit(0d)).alias("night_ratio"),
                        coalesce(col("fraud_count_90d"), lit(0L)).alias("fraud_count_90d"),
                        coalesce(col("profile_as_of"), to_timestamp(lit(asOf))).alias("profile_as_of"));
        return new ProfileBatch(profiles, affectedCustomers);
    }

    record ProfileBatch(Dataset<Row> profiles, Dataset<Row> affectedCustomers) { }

    private static void mergeProfiles(SparkSession spark, Dataset<Row> updates, Dataset<Row> affectedCustomers) {
        String table = "risk_dw.dws_customer_profile";
        if (!spark.catalog().tableExists(table)) {
            updates.write().mode(SaveMode.Overwrite).format("parquet").saveAsTable(table);
            return;
        }
        Dataset<Row> merged = spark.table(table)
                .join(affectedCustomers, new String[] {"customer_id"}, "left_anti")
                .unionByName(updates);
        String staging = table + "_staging_" + UUID.randomUUID().toString().replace("-", "");
        try {
            merged.write().mode(SaveMode.Overwrite).format("parquet").saveAsTable(staging);
            spark.sql("INSERT OVERWRITE TABLE " + table + " SELECT * FROM " + staging);
        } finally {
            spark.sql("DROP TABLE IF EXISTS " + staging);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
