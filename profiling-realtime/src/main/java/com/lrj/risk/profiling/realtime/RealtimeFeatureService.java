package com.lrj.risk.profiling.realtime;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.lrj.risk.common.event.TransactionMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 实时特征计算: 一笔交易 → 更新该账号在 {@code feature:{账号}} Hash 里的窗口特征。
 *
 * <p>当前算子 (对应 PLAN §2.7):
 * <ul>
 *   <li>daily_amount / daily_count: 当日累计金额与笔数 (时间窗口算子, 按日期自动重置)</li>
 *   <li>device_new: 该账号是否首次见到此设备 (关系/去重算子)</li>
 * </ul>
 * 写入即被反欺诈规则 "当日累计金额超限" / "新设备中额交易" 读取。
 */
@Service
public class RealtimeFeatureService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeFeatureService.class);
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 原子地: 跨日则先重置当日累计, 再累加金额与笔数, 返回最新 daily_amount。
     * 单脚本执行避免读-改-写竞态。
     */
    private static final String DAILY_LUA = """
            local key = KEYS[1]
            local today = ARGV[1]
            local amount = tonumber(ARGV[2])
            if redis.call('HGET', key, 'daily_stat_date') ~= today then
                redis.call('HSET', key, 'daily_stat_date', today)
                redis.call('HSET', key, 'daily_amount', 0)
                redis.call('HSET', key, 'daily_count', 0)
            end
            redis.call('HINCRBY', key, 'daily_amount', amount)
            redis.call('HINCRBY', key, 'daily_count', 1)
            return redis.call('HGET', key, 'daily_amount')
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> dailyScript;

    public RealtimeFeatureService(StringRedisTemplate redis) {
        this.redis = redis;
        this.dailyScript = new DefaultRedisScript<>(DAILY_LUA, Long.class);
    }

    public void onTransaction(TransactionMessage msg) {
        String acct = msg.getAccountNo();
        if (acct == null || acct.isBlank()) {
            return;
        }
        String featureKey = "feature:" + acct;
        String today = LocalDate.now().format(YYYYMMDD);

        Long dailyAmount = redis.execute(dailyScript, List.of(featureKey),
                today, String.valueOf(msg.getAmount()));

        // 设备新颖度: SADD 返回 1 表示该账号首次见到此设备
        if (msg.getDeviceId() != null && !msg.getDeviceId().isBlank()) {
            Long added = redis.opsForSet().add("acct:" + acct + ":devices", msg.getDeviceId());
            boolean deviceNew = added != null && added == 1L;
            redis.opsForHash().put(featureKey, "device_new", String.valueOf(deviceNew));
        }

        log.debug("更新实时特征 account={}, daily_amount={}", acct, dailyAmount);
    }
}
