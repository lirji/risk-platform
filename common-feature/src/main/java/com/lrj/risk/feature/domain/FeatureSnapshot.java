package com.lrj.risk.feature.domain;

import java.util.Map;

/** Immutable feature snapshot consumed by the risk domain without performing I/O. */
public final class FeatureSnapshot {

    private final Map<String, Object> features;
    private final boolean available;

    public FeatureSnapshot(Map<String, Object> features) {
        this(features, true);
    }

    private FeatureSnapshot(Map<String, Object> features, boolean available) {
        this.features = features == null ? Map.of() : Map.copyOf(features);
        this.available = available;
    }

    public static FeatureSnapshot empty() {
        return new FeatureSnapshot(Map.of());
    }

    public static FeatureSnapshot unavailable() {
        return new FeatureSnapshot(Map.of(), false);
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isEmpty() {
        return features.isEmpty();
    }

    public Map<String, Object> asMap() {
        return features;
    }

    public Object getRaw(String key) {
        return features.get(key);
    }

    public boolean getBoolean(String key) {
        Object value = features.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && ("true".equalsIgnoreCase(value.toString().trim())
                || "1".equals(value.toString().trim()));
    }

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        Object value = features.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? defaultValue : Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public double getDouble(String key) {
        return getDouble(key, 0d);
    }

    public double getDouble(String key, double defaultValue) {
        Object value = features.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? defaultValue : Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public String getString(String key) {
        Object value = features.get(key);
        return value == null ? null : value.toString();
    }

    @Override
    public String toString() {
        return "FeatureSnapshot" + features;
    }
}
