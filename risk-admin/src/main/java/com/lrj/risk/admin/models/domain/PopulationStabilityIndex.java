package com.lrj.risk.admin.models.domain;

public final class PopulationStabilityIndex {
    private PopulationStabilityIndex() { }

    public static double calculate(long[] baseline, long[] current) {
        if (baseline.length == 0 || baseline.length != current.length) {
            throw new IllegalArgumentException("histograms must be non-empty and have the same bins");
        }
        long baselineTotal = java.util.Arrays.stream(baseline).sum();
        long currentTotal = java.util.Arrays.stream(current).sum();
        if (baselineTotal == 0 || currentTotal == 0) throw new IllegalArgumentException("histograms must have samples");
        double psi = 0;
        for (int index = 0; index < baseline.length; index++) {
            double expected = Math.max(1e-6, (double) baseline[index] / baselineTotal);
            double actual = Math.max(1e-6, (double) current[index] / currentTotal);
            psi += (actual - expected) * Math.log(actual / expected);
        }
        return psi;
    }
}
