package com.lrj.risk.feature.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.lrj.risk.contracts.v1.TransactionEventV1;

/** Shared event-time semantics used by lightweight and Flink implementations. */
public class FeatureAccumulator implements Serializable {

    public static final long VELOCITY_WINDOW_MILLIS = 5 * 60 * 1000L;

    private LocalDate dailyDate;
    private long dailyAmount;
    private long dailyCount;
    private long maxEventTime;
    private final Map<String, Long> recentEvents = new HashMap<>();
    private final Set<String> processedEvents = new HashSet<>();
    private final Set<String> devices = new HashSet<>();
    private final Set<String> counterparties = new HashSet<>();

    public FeatureUpdate apply(TransactionEventV1 event, ZoneId zoneId) {
        if (!processedEvents.add(event.metadata().eventId())) {
            return snapshot(event, false);
        }
        long timestamp = event.eventTime().toEpochMilli();
        LocalDate eventDate = event.eventTime().atZone(zoneId).toLocalDate();
        if (dailyDate == null || eventDate.isAfter(dailyDate)) {
            dailyDate = eventDate;
            dailyAmount = 0;
            dailyCount = 0;
        }
        if (eventDate.equals(dailyDate)) {
            dailyAmount += event.amountMinor();
            dailyCount++;
        }

        maxEventTime = Math.max(maxEventTime, timestamp);
        recentEvents.putIfAbsent(event.metadata().eventId(), timestamp);
        Iterator<Map.Entry<String, Long>> iterator = recentEvents.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < maxEventTime - VELOCITY_WINDOW_MILLIS) {
                iterator.remove();
            }
        }

        boolean deviceNew = event.deviceId() != null && !event.deviceId().isBlank()
                && devices.add(event.deviceId());
        if (event.counterpartyAccount() != null && !event.counterpartyAccount().isBlank()) {
            counterparties.add(event.counterpartyAccount());
        }

        return snapshot(event, deviceNew);
    }

    private FeatureUpdate snapshot(TransactionEventV1 event, boolean deviceNew) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("daily_stat_date", dailyDate.toString().replace("-", ""));
        values.put("daily_amount", Long.toString(dailyAmount));
        values.put("daily_count", Long.toString(dailyCount));
        values.put("txn_count_5m", Integer.toString(recentEvents.size()));
        values.put("device_new", Boolean.toString(deviceNew));
        values.put("known_device_count", Integer.toString(devices.size()));
        values.put("unique_counterparty_count", Integer.toString(counterparties.size()));
        values.put("feature_event_time", Long.toString(maxEventTime));
        return new FeatureUpdate(event.accountNo(), event.metadata().eventId(), values);
    }
}
