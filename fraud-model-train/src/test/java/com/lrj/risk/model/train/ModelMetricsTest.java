package com.lrj.risk.model.train;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ModelMetricsTest {
    @Test
    void computesPerfectRankingMetricsAndConfusionMatrix() {
        ModelMetrics metrics = ModelMetrics.calculate(List.of(
                new ModelMetrics.LabelScore(0, 0.1), new ModelMetrics.LabelScore(0, 0.2),
                new ModelMetrics.LabelScore(1, 0.8), new ModelMetrics.LabelScore(1, 0.9)), 0.5, 0.01);
        assertEquals(1.0, metrics.auc());
        assertEquals(1.0, metrics.ks());
        assertEquals(2, metrics.truePositive());
        assertEquals(2, metrics.trueNegative());
        assertEquals(1.0, metrics.recallAtFixedFpr());
    }

    @Test
    void averagesRanksForTiedScores() {
        ModelMetrics metrics = ModelMetrics.calculate(List.of(
                new ModelMetrics.LabelScore(0, 0.5), new ModelMetrics.LabelScore(1, 0.5)), 0.5, 0.01);
        assertEquals(0.5, metrics.auc());
    }
}
