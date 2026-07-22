package com.lrj.risk.profiling.realtime;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.lrj.risk.contracts.v1.TransactionEventV1;
import com.lrj.risk.feature.domain.FeatureAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Atomic Redis projection with the same event-time semantics as the Flink accumulator. */
@Service
public class RealtimeFeatureService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeFeatureService.class);
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long STATE_TTL_MILLIS = 90L * 24 * 60 * 60 * 1000;

    private static final String UPDATE_LUA = """
            local fkey = KEYS[1]
            local vkey = KEYS[2]
            local dkey = KEYS[3]
            local ckey = KEYS[4]
            local ikey = KEYS[5]
            local eventId = ARGV[1]
            local eventDate = ARGV[2]
            local amount = tonumber(ARGV[3])
            local eventTime = tonumber(ARGV[4])
            local window = tonumber(ARGV[5])
            local device = ARGV[6]
            local counterparty = ARGV[7]
            local ttl = tonumber(ARGV[8])

            if redis.call('SADD', ikey, eventId) == 0 then
                return -1
            end
            redis.call('PEXPIRE', ikey, ttl)

            local currentDate = redis.call('HGET', fkey, 'daily_stat_date')
            if not currentDate or eventDate > currentDate then
                redis.call('HSET', fkey, 'daily_stat_date', eventDate, 'daily_amount', 0, 'daily_count', 0)
                currentDate = eventDate
            end
            if eventDate == currentDate then
                redis.call('HINCRBY', fkey, 'daily_amount', amount)
                redis.call('HINCRBY', fkey, 'daily_count', 1)
            end

            local maxTime = tonumber(redis.call('HGET', fkey, 'feature_event_time') or '0')
            if eventTime > maxTime then maxTime = eventTime end
            redis.call('ZADD', vkey, eventTime, eventId)
            redis.call('ZREMRANGEBYSCORE', vkey, 0, maxTime - window - 1)
            local velocity = redis.call('ZCARD', vkey)

            local deviceNew = 0
            if string.len(device) > 0 then
                deviceNew = redis.call('SADD', dkey, device)
            end
            if string.len(counterparty) > 0 then
                redis.call('SADD', ckey, counterparty)
            end

            redis.call('HSET', fkey,
                'txn_count_5m', velocity,
                'device_new', deviceNew == 1 and 'true' or 'false',
                'known_device_count', redis.call('SCARD', dkey),
                'unique_counterparty_count', redis.call('SCARD', ckey),
                'feature_event_time', maxTime)
            redis.call('PEXPIRE', fkey, ttl)
            redis.call('PEXPIRE', vkey, ttl)
            redis.call('PEXPIRE', dkey, ttl)
            redis.call('PEXPIRE', ckey, ttl)
            return tonumber(redis.call('HGET', fkey, 'daily_amount') or '0')
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> updateScript = new DefaultRedisScript<>(UPDATE_LUA, Long.class);
    private final ZoneId featureZone;

    public RealtimeFeatureService(StringRedisTemplate redis,
                                  @Value("${risk.feature.zone:UTC}") String featureZone) {
        this.redis = redis;
        this.featureZone = ZoneId.of(featureZone);
    }

    public void onTransaction(TransactionEventV1 event) {
        String account = event.accountNo();
        String hashTag = "{" + account + "}";
        String eventDate = event.eventTime().atZone(featureZone).toLocalDate().format(YYYYMMDD);
        Long dailyAmount = redis.execute(updateScript, List.of(
                        "feature:" + hashTag, "vel:" + hashTag, "devices:" + hashTag,
                        "counterparties:" + hashTag, "inbox:realtime-feature:" + hashTag),
                event.metadata().eventId(), eventDate, Long.toString(event.amountMinor()),
                Long.toString(event.eventTime().toEpochMilli()),
                Long.toString(FeatureAccumulator.VELOCITY_WINDOW_MILLIS),
                event.deviceId() == null ? "" : event.deviceId(),
                event.counterpartyAccount() == null ? "" : event.counterpartyAccount(),
                Long.toString(STATE_TTL_MILLIS));
        if (dailyAmount != null && dailyAmount == -1L) {
            log.debug("duplicate transaction event ignored eventId={}", event.metadata().eventId());
        }
    }
}
