package com.lrj.risk.fraud.gateway.adapter.feature;

import java.util.HashMap;
import java.util.Map;

import com.lrj.risk.feature.application.port.FeatureReader;
import com.lrj.risk.feature.domain.FeatureSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis adapter kept outside the shared feature domain. */
@Component
public class RedisFeatureReader implements FeatureReader {

    private static final Logger log = LoggerFactory.getLogger(RedisFeatureReader.class);
    private static final String KEY_PREFIX = "feature:";

    private final StringRedisTemplate redis;

    public RedisFeatureReader(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public FeatureSnapshot fetch(String entityKey) {
        if (entityKey == null || entityKey.isBlank()) {
            return FeatureSnapshot.empty();
        }
        try {
            Map<Object, Object> entries = redis.opsForHash().entries(KEY_PREFIX + "{" + entityKey + "}");
            if (entries == null || entries.isEmpty()) {
                return FeatureSnapshot.empty();
            }
            Map<String, Object> values = new HashMap<>();
            entries.forEach((key, value) -> values.put(String.valueOf(key), value));
            return new FeatureSnapshot(values);
        } catch (RuntimeException exception) {
            log.warn("feature read failed, returning unavailable snapshot cause={}", exception.toString());
            return FeatureSnapshot.unavailable();
        }
    }
}
