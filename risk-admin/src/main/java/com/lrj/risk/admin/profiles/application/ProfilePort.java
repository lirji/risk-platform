package com.lrj.risk.admin.profiles.application;

import java.util.List;
import java.util.Map;

public interface ProfilePort {
    OnlineFeatures onlineFeatures(String accountNo);

    List<Map<String, Object>> definitions(boolean activeFieldsOnly);

    void createTag(String code, String name, String valueType, String definition,
                   long freshnessSeconds, String owner);

    boolean transitionTag(String code, String fromStatus, String toStatus);

    record OnlineFeatures(Map<Object, Object> values, boolean available) { }
}
