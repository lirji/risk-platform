package com.lrj.risk.feature.domain;

import java.io.Serializable;
import java.util.Map;

public record FeatureUpdate(String accountNo, String eventId, Map<String, String> values)
        implements Serializable {

    public FeatureUpdate {
        values = Map.copyOf(values);
    }
}
