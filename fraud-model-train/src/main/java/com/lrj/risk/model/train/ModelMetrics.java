package com.lrj.risk.model.train;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ModelMetrics(double auc, double ks, long truePositive, long falsePositive,
                           long trueNegative, long falseNegative, double precision, double recall,
                           double recallAtFixedFpr, double fixedFpr) {

    public static ModelMetrics calculate(List<LabelScore> examples, double threshold, double fixedFpr) {
        if (examples.isEmpty()) throw new IllegalArgumentException("examples must not be empty");
        long positives = examples.stream().filter(example -> example.label() == 1).count();
        long negatives = examples.size() - positives;
        if (positives == 0 || negatives == 0) throw new IllegalArgumentException("both classes are required");
        long tp = examples.stream().filter(e -> e.label() == 1 && e.score() >= threshold).count();
        long fp = examples.stream().filter(e -> e.label() == 0 && e.score() >= threshold).count();
        long tn = examples.stream().filter(e -> e.label() == 0 && e.score() < threshold).count();
        long fn = examples.stream().filter(e -> e.label() == 1 && e.score() < threshold).count();

        List<LabelScore> sorted = new ArrayList<>(examples);
        sorted.sort(Comparator.comparingDouble(LabelScore::score).reversed());
        double maxKs = 0;
        double recallAtFpr = 0;
        long seenPositive = 0;
        long seenNegative = 0;
        for (LabelScore example : sorted) {
            if (example.label() == 1) seenPositive++; else seenNegative++;
            double tpr = (double) seenPositive / positives;
            double fpr = (double) seenNegative / negatives;
            maxKs = Math.max(maxKs, Math.abs(tpr - fpr));
            if (fpr <= fixedFpr) recallAtFpr = Math.max(recallAtFpr, tpr);
        }

        double rankSum = 0;
        List<LabelScore> ascending = new ArrayList<>(examples);
        ascending.sort(Comparator.comparingDouble(LabelScore::score));
        for (int start = 0; start < ascending.size();) {
            int end = start + 1;
            while (end < ascending.size()
                    && Double.compare(ascending.get(start).score(), ascending.get(end).score()) == 0) end++;
            double averageRank = ((start + 1) + end) / 2.0;
            for (int index = start; index < end; index++) {
                if (ascending.get(index).label() == 1) rankSum += averageRank;
            }
            start = end;
        }
        double auc = (rankSum - positives * (positives + 1) / 2.0) / (positives * negatives);
        double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        double recall = (double) tp / positives;
        return new ModelMetrics(auc, maxKs, tp, fp, tn, fn, precision, recall, recallAtFpr, fixedFpr);
    }

    public record LabelScore(int label, double score) { }
}
