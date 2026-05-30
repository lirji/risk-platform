package com.lrj.risk.model.train;

import java.io.File;

import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.RandomForestClassifier;
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.jpmml.sparkml.PMMLBuilder;

/**
 * 反欺诈随机森林训练 (PLAN M6a)。
 *
 * <p>流程: 读 Hive 训练宽表 {@code risk_dw.dwd_fraud_train} → VectorAssembler + RandomForestClassifier
 * 训练 → 测试集 AUC 评估 → 导出 PMML 到 fraud-engine 资源目录, 供 jpmml-evaluator 线上推理。
 *
 * <p>训练样本来源: 架构 A 离线层 Hive 宽表(数据在 MinIO/S3A, 由 HiveSeedJob 灌示例数据;
 * 生产为真实案件标签 + 历史特征)。特征列须与线上 ModelScorer 组装的字段一一对应。
 *
 * <p>环境变量: HIVE_METASTORE_URIS / S3_ENDPOINT / S3_ACCESS_KEY / S3_SECRET_KEY。
 */
public class FraudModelTrainer {

    /** Hive 训练样本宽表 (列名与线上推理字段严格一致, training-serving 对齐, 见 PLAN §4.2)。 */
    static final String TRAIN_TABLE = "risk_dw.dwd_fraud_train";

    /** 特征列, 与线上推理字段严格一致。 */
    static final String[] FEATURE_COLS = {
            "amount", "daily_amount", "daily_count", "hour", "cross_bank", "device_new"
    };

    public static void main(String[] args) {
        String out = args.length > 0
                ? args[0]
                : "../fraud-engine/src/main/resources/model/fraud-rf.pmml";

        String metastore = System.getenv().getOrDefault("HIVE_METASTORE_URIS", "thrift://localhost:9083");
        String s3Endpoint = System.getenv().getOrDefault("S3_ENDPOINT", "http://localhost:9000");
        String s3Access = System.getenv().getOrDefault("S3_ACCESS_KEY", "minioadmin");
        String s3Secret = System.getenv().getOrDefault("S3_SECRET_KEY", "minioadmin");

        SparkSession spark = SparkSession.builder()
                .appName("fraud-rf-train")
                .master("local[*]")
                .config("spark.ui.enabled", "false")
                .config("spark.sql.catalogImplementation", "hive")
                .config("spark.sql.warehouse.dir", "s3a://warehouse")
                .config("spark.hadoop.hive.metastore.uris", metastore)
                .config("spark.hadoop.fs.s3a.endpoint", s3Endpoint)
                .config("spark.hadoop.fs.s3a.access.key", s3Access)
                .config("spark.hadoop.fs.s3a.secret.key", s3Secret)
                .config("spark.hadoop.fs.s3a.path.style.access", "true")
                .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
                .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
                .enableHiveSupport()
                .getOrCreate();
        spark.sparkContext().setLogLevel("WARN");

        try {
            Dataset<Row> data = spark.table(TRAIN_TABLE);

            Dataset<Row>[] split = data.randomSplit(new double[]{0.8, 0.2}, 42L);
            Dataset<Row> train = split[0];
            Dataset<Row> test = split[1];

            VectorAssembler assembler = new VectorAssembler()
                    .setInputCols(FEATURE_COLS)
                    .setOutputCol("features");
            RandomForestClassifier rf = new RandomForestClassifier()
                    .setLabelCol("label")
                    .setFeaturesCol("features")
                    .setNumTrees(60)
                    .setMaxDepth(6)
                    .setSeed(42L);
            Pipeline pipeline = new Pipeline().setStages(new PipelineStage[]{assembler, rf});

            PipelineModel model = pipeline.fit(train);

            double auc = new BinaryClassificationEvaluator()
                    .setLabelCol("label")
                    .setRawPredictionCol("rawPrediction")
                    .setMetricName("areaUnderROC")
                    .evaluate(model.transform(test));
            long fraud = data.filter("label = 1").count();
            System.out.printf("[训练完成] 样本=%d, 欺诈占比=%.1f%%, 测试集 AUC=%.4f%n",
                    data.count(), 100.0 * fraud / data.count(), auc);

            File outFile = new File(out);
            outFile.getParentFile().mkdirs();
            new PMMLBuilder(data.schema(), model).buildFile(outFile);
            System.out.println("[导出 PMML] " + outFile.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("训练失败", e);
        } finally {
            spark.stop();
        }
    }
}
