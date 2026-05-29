package com.lrj.risk.feature;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的在线特征客户端。
 *
 * <p>特征以 Hash 存放, key 约定为 {@code feature:{entityKey}}, field 为特征名、value 为特征值。
 * 一次 {@code HGETALL} 取齐该实体所有特征 (单次往返, 满足低延迟)。
 *
 * <p>降级: Redis 不可用时捕获异常、返回空快照, 让上层走保守决策而非阻塞 (PLAN §3.6)。
 */
@Component
public class RedisFeatureClient implements FeatureClient {

    private static final Logger log = LoggerFactory.getLogger(RedisFeatureClient.class);
    private static final String KEY_PREFIX = "feature:";

    private final StringRedisTemplate redis;

    public RedisFeatureClient(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public FeatureSnapshot fetch(String entityKey) {
        if (entityKey == null || entityKey.isBlank()) {
            return FeatureSnapshot.empty();
        }
        try {
            Map<Object, Object> entries = redis.opsForHash().entries(KEY_PREFIX + entityKey);
            if (entries == null || entries.isEmpty()) {
                return FeatureSnapshot.empty();
            }
            Map<String, Object> features = new HashMap<>(entries.size() * 2);
            entries.forEach((k, v) -> features.put(String.valueOf(k), v));
            return new FeatureSnapshot(features);
        } catch (Exception e) {
            // 降级: 特征拉取失败不阻塞主链路, 返回空快照交由规则走保守路径
            log.warn("特征拉取失败, 降级返回空快照 entityKey={}, cause={}", entityKey, e.toString());
            return FeatureSnapshot.empty();
        }
    }
}
