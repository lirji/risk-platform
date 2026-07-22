package com.lrj.risk.profiling.flink;

import com.lrj.risk.feature.domain.FeatureUpdate;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import redis.clients.jedis.JedisPool;

/** Absolute HSET projection is idempotent when Flink replays after a checkpoint. */
public class RedisFeatureSink extends RichSinkFunction<FeatureUpdate> {

    private transient JedisPool jedisPool;

    @Override
    public void open(OpenContext openContext) {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        jedisPool = new JedisPool(host, port);
    }

    @Override
    public void invoke(FeatureUpdate update, Context context) {
        try (var jedis = jedisPool.getResource()) {
            jedis.hset("feature:{" + update.accountNo() + "}", update.values());
        }
    }

    @Override
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
