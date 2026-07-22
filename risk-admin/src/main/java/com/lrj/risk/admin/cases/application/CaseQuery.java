package com.lrj.risk.admin.cases.application;

import java.util.List;
import java.util.Map;

public interface CaseQuery {
    List<Map<String, Object>> findByStatus(String status, int limit);
}
