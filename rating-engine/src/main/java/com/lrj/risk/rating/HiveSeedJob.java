package com.lrj.risk.rating;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
 * 离线层 Hive 宽表 seeding (架构 A): 建库 + 两张 EXTERNAL 表并灌示例数据。
 *
 * <p>存算分离: 元数据进 Hive Metastore (thrift), parquet 数据由 Spark 侧写到 MinIO/S3A。
 * 用 saveAsTable + 显式 path 选项建成 EXTERNAL 表 —— Spark 做 S3 IO, metastore 只记 LOCATION,
 * 故 metastore 容器无需任何 S3 配置 (见 HIVE-INTEGRATION-PLAN.md §2)。
 *
 * <ul>
 *   <li>{@code risk_dw.dwd_cust_feature}: 客户离线画像宽表, 供 RatingEngineJob JOIN ES 标签评级</li>
 *   <li>{@code risk_dw.dwd_fraud_train}: 反欺诈训练样本宽表, 供 FraudModelTrainer 训练 (列与线上 ModelScorer 对齐)</li>
 * </ul>
 *
 * <p>环境变量: HIVE_METASTORE_URIS / S3_ENDPOINT / S3_ACCESS_KEY / S3_SECRET_KEY / WAREHOUSE_BASE。
 */
public class HiveSeedJob {

    public static void main(String[] args) {
        String warehouseBase = System.getenv().getOrDefault("WAREHOUSE_BASE", "s3a://warehouse/risk_dw");

        SparkSession spark = SparkSessions.builder("hive-seed");
        try {
            // 幂等重灌: 删库重建并显式落 s3a, 避免残留的本地 location
            spark.sql("DROP DATABASE IF EXISTS risk_dw CASCADE");
            spark.sql("CREATE DATABASE risk_dw LOCATION '" + warehouseBase + "'");

            seedCustFeature(spark, warehouseBase + "/dwd_cust_feature");
            seedFraudTrain(spark, warehouseBase + "/dwd_fraud_train");

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
