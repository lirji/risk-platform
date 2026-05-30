package com.lrj.risk.model.train;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.RandomForestClassifier;
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.jpmml.sparkml.PMMLBuilder;

/**
 * 反欺诈随机森林训练 (PLAN M6a)。
 *
 * <p>流程: 合成带信号的标注交易数据 → VectorAssembler + RandomForestClassifier 训练
 * → 测试集 AUC 评估 → 导出 PMML 到 fraud-engine 资源目录, 供 jpmml-evaluator 线上推理。
 *
 * <p>无真实案件标签时用合成数据(埋入"高额/大额累计/新设备/凌晨/跨行→欺诈"信号);
 * 生产改为从 Hive 宽表读 (PLAN §4.2)。
 *
 * <p>特征列须与线上 ModelScorer 组装的字段一一对应。
 */
public class FraudModelTrainer {

    /** 特征列, 与线上推理字段严格一致 (training-serving 对齐, 见 PLAN §4.2)。 */
    static final String[] FEATURE_COLS = {
            "amount", "daily_amount", "daily_count", "hour", "cross_bank", "device_new"
    };

    public static void main(String[] args) {
        String out = args.length > 0
                ? args[0]
                : "../fraud-engine/src/main/resources/model/fraud-rf.pmml";

        SparkSession spark = SparkSession.builder()
                .appName("fraud-rf-train")
                .master("local[*]")
                .config("spark.ui.enabled", "false")
                .getOrCreate();
        spark.sparkContext().setLogLevel("WARN");

        try {
            Dataset<Row> data = spark.createDataFrame(synthesize(5000), schema());

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

    private static StructType schema() {
        return new StructType(new StructField[]{
                new StructField("amount", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("daily_amount", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("daily_count", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("hour", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("cross_bank", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("device_new", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("label", DataTypes.DoubleType, false, Metadata.empty())
        });
    }

    /** 合成带信号的标注数据: 高额/大额累计/新设备/凌晨/跨行 → 更可能欺诈。 */
    private static List<Row> synthesize(int n) {
        List<Row> rows = new ArrayList<>(n);
        Random rnd = new Random(42);
        for (int i = 0; i < n; i++) {
            double amount = Math.abs(rnd.nextGaussian()) * 30000 + 2000;       // 元
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
