package com.lrj.risk.model.train;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.contracts.v1.ModelFeatureSchemaV1;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.RandomForestClassifier;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.jpmml.sparkml.PMMLBuilder;

import static org.apache.spark.sql.functions.*;

/** Case-label driven model training with deterministic metrics and versioned artifacts. */
public final class FraudModelTrainer {

    static final String[] FEATURE_COLS = ModelFeatureSchemaV1.FEATURES.toArray(String[]::new);

    private FraudModelTrainer() { }

    public static void main(String[] args) {
        String version = System.getenv().getOrDefault("MODEL_VERSION", Instant.now().toString().replace(':', '-'));
        Path outputDirectory = Path.of(args.length > 0 ? args[0] : "artifacts/fraud-rf/" + version);
        SparkSession spark = session();
        try {
            String featureTable = System.getenv().getOrDefault("TRAIN_FEATURE_TABLE", "risk_dw.dwd_decision_feature");
            String labelTable = System.getenv().getOrDefault("TRAIN_LABEL_TABLE", "risk_dw.fact_case_label");
            Dataset<Row> features = spark.table(featureTable);
            Dataset<Row> labels = spark.table(labelTable).select(col("source_id"), col("txn_id"),
                    when(col("label").equalTo("FRAUD"), 1.0).otherwise(0.0).alias("label"));
            Dataset<Row> data = features.join(labels, new String[]{"source_id", "txn_id"}, "inner").na().drop();
            TrainingResult training = train(data, 80);
            long total = training.trainingRows();
            long positive = training.positiveRows();
            PipelineModel model = training.model();
            ModelMetrics metrics = training.metrics();

            Files.createDirectories(outputDirectory);
            File pmml = outputDirectory.resolve("model.pmml").toFile();
            new PMMLBuilder(training.weightedData().schema(), model).buildFile(pmml);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                    outputDirectory.resolve("metrics.json").toFile(), metrics);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                    outputDirectory.resolve("manifest.json").toFile(), java.util.Map.of(
                            "modelCode", "fraud-rf", "version", version, "trainingRows", total,
                            "positiveRows", positive, "featureColumns", FEATURE_COLS,
                            "featureTable", featureTable, "labelTable", labelTable,
                            "createdAt", Instant.now().toString()));
            System.out.printf("[model] version=%s rows=%d auc=%.4f ks=%.4f recall@fpr=%.4f artifact=%s%n",
                    version, total, metrics.auc(), metrics.ks(), metrics.recallAtFixedFpr(), pmml);
        } catch (Exception exception) {
            throw new RuntimeException("training failed", exception);
        } finally {
            spark.stop();
        }
    }

    static TrainingResult train(Dataset<Row> input, int numberOfTrees) {
        long total = input.count();
        long positive = input.filter(col("label").equalTo(1.0)).count();
        long negative = total - positive;
        if (total < 20 || positive < 5 || negative < 5) {
            throw new IllegalStateException(
                    "training requires at least 20 labeled rows and at least 5 rows per class");
        }
        double positiveWeight = (double) negative / positive;
        Dataset<Row> weighted = input.withColumn("classWeight",
                when(col("label").equalTo(1.0), positiveWeight).otherwise(1.0));
        Dataset<Row> partitioned = weighted.withColumn("_class_row",
                row_number().over(Window.partitionBy("label")
                        .orderBy(xxhash64(col("source_id"), col("txn_id")))));
        Dataset<Row> training = partitioned.filter(not(pmod(col("_class_row"), lit(5)).equalTo(0)))
                .drop("_class_row");
        Dataset<Row> validation = partitioned.filter(pmod(col("_class_row"), lit(5)).equalTo(0))
                .drop("_class_row");

        VectorAssembler assembler = new VectorAssembler().setInputCols(FEATURE_COLS).setOutputCol("features");
        RandomForestClassifier classifier = new RandomForestClassifier().setLabelCol("label")
                .setFeaturesCol("features").setWeightCol("classWeight")
                .setNumTrees(numberOfTrees).setMaxDepth(7).setSeed(42L);
        PipelineModel model = new Pipeline().setStages(new PipelineStage[]{assembler, classifier}).fit(training);
        List<ModelMetrics.LabelScore> scores = model.transform(validation).select("label", "probability")
                .collectAsList().stream().map(row -> new ModelMetrics.LabelScore(
                        ((Number) row.getAs("label")).intValue(), ((Vector) row.getAs("probability")).apply(1)))
                .toList();
        return new TrainingResult(model, weighted, ModelMetrics.calculate(scores, 0.5, 0.01), total, positive);
    }

    record TrainingResult(PipelineModel model, Dataset<Row> weightedData, ModelMetrics metrics,
                          long trainingRows, long positiveRows) { }

    private static SparkSession session() {
        return SparkSession.builder().appName("fraud-rf-train")
                .master(System.getenv().getOrDefault("SPARK_MASTER", "local[*]"))
                .config("spark.ui.enabled", "false").config("spark.sql.session.timeZone", "UTC")
                .config("spark.sql.catalogImplementation", "hive")
                .config("spark.sql.warehouse.dir", "s3a://warehouse")
                .config("spark.hadoop.hive.metastore.uris", System.getenv().getOrDefault("HIVE_METASTORE_URIS", "thrift://localhost:9083"))
                .config("spark.hadoop.fs.s3a.endpoint", System.getenv().getOrDefault("S3_ENDPOINT", "http://localhost:9000"))
                .config("spark.hadoop.fs.s3a.access.key", requiredEnv("S3_ACCESS_KEY"))
                .config("spark.hadoop.fs.s3a.secret.key", requiredEnv("S3_SECRET_KEY"))
                .config("spark.hadoop.fs.s3a.path.style.access", "true")
                .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
                .enableHiveSupport().getOrCreate();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
