package com.lrj.risk.admin.audit.application;

import java.util.List;
import java.util.Map;

public interface AuditQuery {
    List<Map<String, Object>> latest(int limit);
}
