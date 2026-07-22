package com.lrj.risk.profiling.offline;

import static org.apache.spark.sql.functions.callUDF;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.from_json;
import static org.apache.spark.sql.functions.get_json_object;
import static org.apache.spark.sql.functions.hour;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.to_timestamp;
import static org.apache.spark.sql.functions.when;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

/**
 * Incrementally converts immutable MySQL Decision/Outbox data and authoritative case labels into
 * privacy-safe Hive facts. Raw account values are used only in executor memory and never persisted.
 */
public final class RiskFactIngestionJob {
    private RiskFactIngestionJob() { }

    public static void main(String[] args) {
        String watermark = Timestamp.from(Instant.parse(
                System.getenv().getOrDefault("FACT_WATERMARK", "1970-01-01T00:00:00Z"))).toString();
        String hmacKey = requiredEnv("PII_HMAC_KEY");
        SparkSession spark = session();
        spark.udf().register("risk_hmac", (UDF1<String, String>) value -> hmac(value, hmacKey),
                DataTypes.StringType);
        try {
            Dataset<Row> transactionOutbox = mysql(spark, """
                    SELECT payload FROM outbox_event
                     WHERE topic='transaction.v1' AND created_at >= '%s'
                    """.formatted(watermark));
            Dataset<Row> decisions = mysql(spark, """
                    SELECT source_id, txn_id, account_token, feature_snapshot_json, created_at
                      FROM risk_decision WHERE created_at >= '%s'
                    """.formatted(watermark));
            Dataset<Row> labels = mysql(spark, """
                    SELECT d.source_id, d.txn_id, f.label, f.created_at
                      FROM case_label_feedback f JOIN risk_decision d ON d.decision_id=f.decision_id
                     WHERE f.created_at >= '%s'
                    """.formatted(watermark));

            StructType metadata = new StructType()
                    .add("sourceId", DataTypes.StringType).add("txnId", DataTypes.StringType);
            StructType transactionSchema = new StructType()
                    .add("metadata", metadata)
                    .add("accountNo", DataTypes.StringType)
                    .add("counterpartyAccount", DataTypes.StringType)
                    .add("amountMinor", DataTypes.LongType)
                    .add("deviceId", DataTypes.StringType)
                    .add("eventTime", DataTypes.StringType);
            Dataset<Row> transactions = transactionOutbox
                    .withColumn("event", from_json(col("payload"), transactionSchema))
                    .select(col("event.metadata.sourceId").alias("source_id"),
                            col("event.metadata.txnId").alias("txn_id"),
                            col("event.counterpartyAccount").alias("counterparty_account"),
                            col("event.amountMinor").alias("amount_minor"),
                            col("event.deviceId").alias("device_id"),
                            to_timestamp(col("event.eventTime")).alias("event_time"));

            Dataset<Row> joined = transactions.join(decisions,
                    new String[] {"source_id", "txn_id"}, "inner");
            Dataset<Row> transactionFacts = joined.select(
                    col("source_id"), col("txn_id"), col("account_token").alias("customer_id"),
                    when(col("counterparty_account").isNull(), lit(null))
                            .otherwise(callUDF("risk_hmac", col("counterparty_account")))
                            .alias("counterparty_id"),
                    col("amount_minor"), col("event_time"));

            Dataset<Row> decisionFeatures = joined.select(
                    col("source_id"), col("txn_id"),
                    col("amount_minor").cast("double").divide(100d).alias("amount"),
                    get_json_object(col("feature_snapshot_json"), "$.daily_amount")
                            .cast("double").divide(100d).alias("daily_amount"),
                    get_json_object(col("feature_snapshot_json"), "$.daily_count")
                            .cast("double").alias("daily_count"),
                    hour(col("event_time")).cast("double").alias("hour"),
                    when(col("counterparty_account").isNotNull(), 1d).otherwise(0d).alias("cross_bank"),
                    when(get_json_object(col("feature_snapshot_json"), "$.device_new").equalTo("true"), 1d)
                            .otherwise(0d).alias("device_new"));
            Dataset<Row> labelFacts = labels.select("source_id", "txn_id", "label", "created_at");

            mergeImmutable(spark, "risk_dw.dwd_transaction_fact", transactionFacts,
                    new String[] {"source_id", "txn_id"});
            mergeImmutable(spark, "risk_dw.dwd_decision_feature", decisionFeatures,
                    new String[] {"source_id", "txn_id"});
            mergeImmutable(spark, "risk_dw.fact_case_label", labelFacts,
                    new String[] {"source_id", "txn_id"});
        } finally {
            spark.stop();
        }
    }

    private static Dataset<Row> mysql(SparkSession spark, String query) {
        return spark.read().format("jdbc")
                .option("url", System.getenv().getOrDefault("MYSQL_URL",
                        "jdbc:mysql://localhost:13307/risk_platform?useSSL=false&allowPublicKeyRetrieval=true"))
                .option("user", System.getenv().getOrDefault("MYSQL_USER", "root"))
                .option("password", requiredEnv("MYSQL_PWD"))
                .option("driver", "com.mysql.cj.jdbc.Driver")
                .option("dbtable", "(" + query + ") risk_increment")
                .load();
    }

    private static void mergeImmutable(SparkSession spark, String table, Dataset<Row> incoming, String[] keys) {
        if (!spark.catalog().tableExists(table)) {
            incoming.write().mode("overwrite").format("parquet").saveAsTable(table);
            return;
        }
        Dataset<Row> incomingKeys = incoming.selectExpr(keys).distinct();
        Dataset<Row> merged = spark.table(table).join(incomingKeys, keys, "left_anti").unionByName(incoming);
        String staging = table + "_staging_" + UUID.randomUUID().toString().replace("-", "");
        try {
            merged.write().mode("overwrite").format("parquet").saveAsTable(staging);
            spark.sql("INSERT OVERWRITE TABLE " + table + " SELECT * FROM " + staging);
        } finally {
            spark.sql("DROP TABLE IF EXISTS " + staging);
        }
    }

    private static SparkSession session() {
        return SparkSession.builder().appName("risk-fact-ingestion")
                .master(System.getenv().getOrDefault("SPARK_MASTER", "local[*]"))
                .config("spark.ui.enabled", "false").config("spark.sql.session.timeZone", "UTC")
                .config("spark.sql.catalogImplementation", "hive")
                .config("spark.hadoop.hive.metastore.uris",
                        System.getenv().getOrDefault("HIVE_METASTORE_URIS", "thrift://localhost:9083"))
                .config("spark.sql.warehouse.dir", "s3a://warehouse")
                .config("spark.hadoop.fs.s3a.endpoint",
                        System.getenv().getOrDefault("S3_ENDPOINT", "http://localhost:9000"))
                .config("spark.hadoop.fs.s3a.access.key", requiredEnv("S3_ACCESS_KEY"))
                .config("spark.hadoop.fs.s3a.secret.key", requiredEnv("S3_SECRET_KEY"))
                .config("spark.hadoop.fs.s3a.path.style.access", "true")
                .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
                .enableHiveSupport().getOrCreate();
    }

    private static String hmac(String value, String key) throws Exception {
        if (value == null || value.isBlank()) return null;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
