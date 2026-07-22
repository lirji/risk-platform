package com.lrj.risk.rating;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.sql.Timestamp;
import java.time.Instant;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * 仅用于本地开发的 Hive fixture：建库并按正式事实表结构灌入确定性脱敏数据。
 *
 * <p>存算分离: 元数据进 Hive Metastore (thrift), parquet 数据由 Spark 侧写到 MinIO/S3A。
 * 用 saveAsTable + 显式 path 选项建成 EXTERNAL 表 —— Spark 做 S3 IO, metastore 只记 LOCATION,
 * 故 metastore 容器无需任何 S3 配置 (见 HIVE-INTEGRATION-PLAN.md §2)。
 *
 * <ul>
 *   <li>{@code risk_dw.dwd_cust_feature}: 客户离线画像宽表, 供 RatingEngineJob JOIN ES 标签评级</li>
 *   <li>{@code risk_dw.dwd_transaction_fact/dwd_decision_feature/fact_case_label}: 正式离线画像与训练输入契约</li>
 *   <li>{@code risk_dw.dwd_fraud_train}: 旧版兼容训练 fixture，不再是正式训练入口</li>
 * </ul>
 *
 * <p>环境变量: HIVE_METASTORE_URIS / S3_ENDPOINT / S3_ACCESS_KEY / S3_SECRET_KEY / WAREHOUSE_BASE。
 */
public class HiveSeedJob {

    public static void main(String[] args) {
        if (!"true".equalsIgnoreCase(System.getenv("ALLOW_FIXTURE_SEED"))) {
            throw new IllegalStateException("refusing to overwrite local fixture tables without ALLOW_FIXTURE_SEED=true");
        }
        String warehouseBase = System.getenv().getOrDefault("WAREHOUSE_BASE", "s3a://warehouse/risk_dw");

        SparkSession spark = SparkSessions.builder("hive-seed");
        try {
            spark.sql("CREATE DATABASE IF NOT EXISTS risk_dw LOCATION '" + warehouseBase + "'");

            seedCustFeature(spark, warehouseBase + "/dwd_cust_feature");
            seedFraudTrain(spark, warehouseBase + "/dwd_fraud_train");
            seedDecisionLifecycleFacts(spark, warehouseBase);

            System.out.println("[hive-seed] 完成。表清单:");
            spark.sql("SHOW TABLES IN risk_dw").show(false);
        } finally {
            spark.stop();
        }
    }

    /** 客户离线画像宽表: 规则消费 amount_90d/txn_cnt_90d/counterparty, 其余为宽表描述列。 */
    private static void seedCustFeature(SparkSession spark, String path) {
        StructType schema = new StructType(new StructField[]{
                new StructField("cust_id", DataTypes.StringType, false, Metadata.empty()),
                new StructField("amount_90d", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("txn_cnt_90d", DataTypes.LongType, false, Metadata.empty()),
                new StructField("counterparty", DataTypes.LongType, false, Metadata.empty()),
                new StructField("txn_amt_30d", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("avg_amt", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("recency_days", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("device_cnt", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("account_age_days", DataTypes.IntegerType, false, Metadata.empty())
        });
        List<Row> rows = List.of(
                // 高风险(应评D): 大额+高频+多对手
                RowFactory.create("C001", 5000000d, 350L, 80L, 1800000d, 14285d, 0, 6, 400),
                // 中风险(应评B): 仅大额命中
                RowFactory.create("C002", 1200000d, 60L, 10L, 400000d, 20000d, 2, 2, 900),
                // 低风险(应评A): 均不命中
                RowFactory.create("C003", 30000d, 5L, 2L, 10000d, 6000d, 20, 1, 1500)
        );
        write(spark.createDataFrame(rows, schema), "risk_dw.dwd_cust_feature", path);
    }

    /** 反欺诈训练样本宽表: 列名与线上 ModelScorer 严格一致 (training-serving 对齐)。 */
    private static void seedFraudTrain(SparkSession spark, String path) {
        StructType schema = new StructType(new StructField[]{
                new StructField("amount", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("daily_amount", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("daily_count", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("hour", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("cross_bank", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("device_new", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("label", DataTypes.DoubleType, false, Metadata.empty())
        });
        write(spark.createDataFrame(synthesize(5000), schema), "risk_dw.dwd_fraud_train", path);
    }

    /** Fixtures match the immutable tables produced by profiling-offline/RiskFactIngestionJob. */
    private static void seedDecisionLifecycleFacts(SparkSession spark, String warehouseBase) {
        StructType transactionSchema = new StructType(new StructField[]{
                new StructField("source_id", DataTypes.StringType, false, Metadata.empty()),
                new StructField("txn_id", DataTypes.StringType, false, Metadata.empty()),
                new StructField("customer_id", DataTypes.StringType, false, Metadata.empty()),
                new StructField("counterparty_id", DataTypes.StringType, true, Metadata.empty()),
                new StructField("amount_minor", DataTypes.LongType, false, Metadata.empty()),
                new StructField("event_time", DataTypes.TimestampType, false, Metadata.empty())
        });
        StructType featureSchema = new StructType(new StructField[]{
                new StructField("source_id", DataTypes.StringType, false, Metadata.empty()),
                new StructField("txn_id", DataTypes.StringType, false, Metadata.empty()),
                new StructField("amount", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("daily_amount", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("daily_count", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("hour", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("cross_bank", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("device_new", DataTypes.DoubleType, false, Metadata.empty())
        });
        StructType labelSchema = new StructType(new StructField[]{
                new StructField("source_id", DataTypes.StringType, false, Metadata.empty()),
                new StructField("txn_id", DataTypes.StringType, false, Metadata.empty()),
                new StructField("label", DataTypes.StringType, false, Metadata.empty()),
                new StructField("created_at", DataTypes.TimestampType, false, Metadata.empty())
        });
        List<Row> transactions = new ArrayList<>();
        List<Row> features = new ArrayList<>();
        List<Row> labels = new ArrayList<>();
        Random random = new Random(42);
        Instant base = Instant.now().minusSeconds(30L * 24 * 60 * 60);
        for (int index = 0; index < 5_000; index++) {
            double amount = Math.abs(random.nextGaussian()) * 30_000 + 2_000;
            double dailyAmount = Math.abs(random.nextGaussian()) * 50_000 + amount;
            double dailyCount = random.nextInt(20);
            double hour = random.nextInt(24);
            double crossBank = random.nextDouble() < 0.4 ? 1d : 0d;
            double deviceNew = random.nextDouble() < 0.2 ? 1d : 0d;
            double signal = -3 + 0.00002 * amount + 0.000015 * dailyAmount + 1.2 * deviceNew
                    + 0.6 * crossBank + (hour < 6 ? 1 : 0) + 0.05 * dailyCount;
            boolean fraud = random.nextDouble() < 1d / (1d + Math.exp(-signal));
            String txnId = "fixture-txn-" + index;
            String customerId = "fixture-customer-token-" + index % 200;
            Instant eventTime = base.plusSeconds(index * 300L);
            transactions.add(RowFactory.create("FIXTURE", txnId, customerId,
                    crossBank == 1d ? "fixture-counterparty-token-" + index % 400 : null,
                    Math.round(amount * 100), Timestamp.from(eventTime)));
            features.add(RowFactory.create("FIXTURE", txnId, amount, dailyAmount, dailyCount,
                    hour, crossBank, deviceNew));
            labels.add(RowFactory.create("FIXTURE", txnId, fraud ? "FRAUD" : "NORMAL",
                    Timestamp.from(eventTime.plusSeconds(60))));
        }
        write(spark.createDataFrame(transactions, transactionSchema), "risk_dw.dwd_transaction_fact",
                warehouseBase + "/dwd_transaction_fact");
        write(spark.createDataFrame(features, featureSchema), "risk_dw.dwd_decision_feature",
                warehouseBase + "/dwd_decision_feature");
        write(spark.createDataFrame(labels, labelSchema), "risk_dw.fact_case_label",
                warehouseBase + "/fact_case_label");
    }

    /** EXTERNAL 表落地: 显式 path → Spark 写 S3, metastore 只记 LOCATION 不碰 S3。 */
    private static void write(Dataset<Row> df, String table, String path) {
        df.write()
                .mode(SaveMode.Overwrite)
                .format("parquet")
                .option("path", path)
                .saveAsTable(table);
        System.out.printf("[hive-seed] %s ← %d 行 @ %s%n", table, df.count(), path);
    }

    /**
     * 合成带信号的标注交易: 高额/大额累计/新设备/凌晨/跨行 → 更可能欺诈。
     * 口径与 FraudModelTrainer 原合成逻辑一致, 仅落点改为 Hive 表。
     */
    private static List<Row> synthesize(int n) {
        List<Row> rows = new ArrayList<>(n);
        Random rnd = new Random(42);
        for (int i = 0; i < n; i++) {
            double amount = Math.abs(rnd.nextGaussian()) * 30000 + 2000;
            double dailyAmount = Math.abs(rnd.nextGaussian()) * 50000 + amount;
            double dailyCount = rnd.nextInt(20);
            double hour = rnd.nextInt(24);
            double crossBank = rnd.nextDouble() < 0.4 ? 1.0 : 0.0;
            double deviceNew = rnd.nextDouble() < 0.2 ? 1.0 : 0.0;

            double z = -3.0
                    + 0.00002 * amount
                    + 0.000015 * dailyAmount
                    + 1.2 * deviceNew
                    + 0.6 * crossBank
                    + (hour < 6 ? 1.0 : 0.0)
                    + 0.05 * dailyCount;
            double p = 1.0 / (1.0 + Math.exp(-z));
            double label = rnd.nextDouble() < p ? 1.0 : 0.0;

            rows.add(RowFactory.create(amount, dailyAmount, dailyCount, hour, crossBank, deviceNew, label));
        }
        return rows;
    }
}
