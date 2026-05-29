package com.lrj.risk.feature;

import java.util.Collections;
import java.util.Map;

/**
 * 特征快照: 某个实体(账号/卡号)在一次决策时刻的特征集合。
 *
 * <p>由 {@link FeatureClient} 从在线特征存储(Redis)一次取齐, 进规则引擎前准备好,
 * 规则/模型只读它、不再做 IO (见 PLAN §3.6 低延迟设计)。
 *
 * <p>底层是 {@code Map<String,Object>}, 值可能是字符串(Redis hash 取出的原始值),
 * 故 getter 做容错解析, 缺失/解析失败返回默认值——对应"特征部分缺失时偏保守"的降级策略。
 */
public class FeatureSnapshot {

    private final Map<String, Object> features;

    public FeatureSnapshot(Map<String, Object> features) {
        this.features = features == null ? Collections.emptyMap() : features;
    }

    public static FeatureSnapshot empty() {
        return new FeatureSnapshot(Collections.emptyMap());
    }

    public boolean isEmpty() {
        return features.isEmpty();
    }

    public Object getRaw(String key) {
        return features.get(key);
    }

    public boolean getBoolean(String key) {
        Object v = features.get(key);
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = v.toString().trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        Object v = features.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key) {
        return getDouble(key, 0d);
    }

    public double getDouble(String key, double defaultValue) {
        Object v = features.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getString(String key) {
        Object v = features.get(key);
        return v == null ? null : v.toString();
    }

    @Override
    public String toString() {
        return "FeatureSnapshot" + features;
    }
}
