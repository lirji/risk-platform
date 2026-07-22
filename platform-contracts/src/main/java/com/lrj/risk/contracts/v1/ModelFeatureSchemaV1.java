package com.lrj.risk.contracts.v1;

import java.util.List;

public final class ModelFeatureSchemaV1 {
    public static final String VERSION = "fraud-feature-v1";
    public static final List<String> FEATURES = List.of(
            "amount", "daily_amount", "daily_count", "hour", "cross_bank", "device_new");
    private ModelFeatureSchemaV1() { }
}
