package com.lrj.risk.admin.operations.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface DeadEventPort {
    List<Map<String, Object>> findDead();

    boolean requestReplay(String eventId, Instant requestedAt);
}
