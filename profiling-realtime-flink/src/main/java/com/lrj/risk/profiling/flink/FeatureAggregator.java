package com.lrj.risk.profiling.flink;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lrj.risk.common.event.TransactionMessage;

import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import redis.clients.jedis.JedisPool;

/**
 * 按账号键控聚合实时特征, 状态由 Flink 管理 (替代轻量版的 Redis Lua), Redis 仅作在线服务出口。
 *
 * <p>算子: 当日累计金额/笔数 (跨日重置) + 5 分钟滑动窗口交易数。写回 feature:{账号} 供反欺诈读取。
 */
public class FeatureAggregator extends KeyedProcessFunction<String, TransactionMessage, Void> {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long WINDOW_5M_MS = 5 * 60 * 1000L;

    private transient ValueState<Long> dailyAmount;
    private transient ValueState<Long> dailyCount;
    private transient ValueState<String> dailyDate;
    private transient ListState<Long> recentTs;   // 5 分钟内事件时间戳
    private transient JedisPool jedisPool;

    @Override
    public void open(OpenContext openContext) {
        dailyAmount = getRuntimeContext().getState(new ValueStateDescriptor<>("dailyAmount", Long.class));
        dailyCount = getRuntimeContext().getState(new ValueStateDescriptor<>("dailyCount", Long.class));
        dailyDate = getRuntimeContext().getState(new ValueStateDescriptor<>("dailyDate", String.class));
        recentTs = getRuntimeContext().getListState(new ListStateDescriptor<>("recentTs", Long.class));

        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        jedisPool = new JedisPool(host, port);
    }

    @Override
    public void processElement(TransactionMessage msg, Context ctx, Collector<Void> out) throws Exception {
        String today = LocalDate.now().format(YYYYMMDD);
        if (!today.equals(dailyDate.value())) {
            dailyDate.update(today);
            dailyAmount.update(0L);
            dailyCount.update(0L);
        }
        long amount = (dailyAmount.value() == null ? 0L : dailyAmount.value()) + msg.getAmount();
        long count = (dailyCount.value() == null ? 0L : dailyCount.value()) + 1;
        dailyAmount.update(amount);
        dailyCount.update(count);

        // 5 分钟滑窗: 追加当前时间, 剔除窗口外
        long now = msg.getEventTime() > 0 ? msg.getEventTime() : System.currentTimeMillis();
        List<Long> kept = new ArrayList<>();
        kept.add(now);
        for (Long ts : recentTs.get()) {
            if (ts >= now - WINDOW_5M_MS) {
                kept.add(ts);
            }
        }
        recentTs.update(kept);

        Map<String, String> feature = new HashMap<>();
        feature.put("daily_amount", String.valueOf(amount));
        feature.put("daily_count", String.valueOf(count));
        feature.put("txn_count_5m", String.valueOf(kept.size()));
        try (var jedis = jedisPool.getResource()) {
            jedis.hset("feature:" + msg.getAccountNo(), feature);
        }
    }

    @Override
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
