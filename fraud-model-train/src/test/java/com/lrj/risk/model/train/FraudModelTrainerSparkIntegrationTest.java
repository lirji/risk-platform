package com.lrj.risk.model.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FraudModelTrainerSparkIntegrationTest {

    private static SparkSession spark;

    @BeforeAll
    static void startSpark() {
        spark = SparkSession.builder().master("local[1]").appName("fraud-model-trainer-test")
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
    void trainsWithStratifiedDeterministicSplitAndClassWeights() {
        StructType schema = new StructType()
                .add("source_id", DataTypes.StringType, false)
                .add("txn_id", DataTypes.StringType, false)
                .add("amount", DataTypes.DoubleType, false)
                .add("daily_amount", DataTypes.DoubleType, false)
                .add("daily_count", DataTypes.DoubleType, false)
                .add("hour", DataTypes.DoubleType, false)
                .add("cross_bank", DataTypes.DoubleType, false)
                .add("device_new", DataTypes.DoubleType, false)
                .add("label", DataTypes.DoubleType, false);
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            double label = index < 15 ? 1d : 0d;
            double signal = label == 1d ? 100d + index : 1d + index / 100d;
            rows.add(RowFactory.create("MOBILE", "txn-" + index, signal, signal * 2,
                    label == 1d ? 8d : 1d, label == 1d ? 2d : 12d, label, label, label));
        }

        var first = FraudModelTrainer.train(spark.createDataFrame(rows, schema), 12);
        var second = FraudModelTrainer.train(spark.createDataFrame(rows, schema), 12);

        assertEquals(50, first.trainingRows());
        assertEquals(15, first.positiveRows());
        assertTrue(first.metrics().auc() >= 0.9d);
        assertEquals(first.metrics(), second.metrics());
    }
}
