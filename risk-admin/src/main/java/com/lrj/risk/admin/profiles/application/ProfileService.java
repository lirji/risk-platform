package com.lrj.risk.admin.profiles.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final ProfilePort profiles;

    public ProfileService(ProfilePort profiles) {
        this.profiles = profiles;
    }

    public Map<String, Object> profile(String accountNo) {
        ProfilePort.OnlineFeatures features = profiles.onlineFeatures(accountNo);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accountNo", mask(accountNo));
        response.put("onlineFeatures", features.values());
        response.put("available", features.available());
        response.put("definitions", profiles.definitions(true));
        return response;
    }

    public List<Map<String, Object>> tags() {
        return profiles.definitions(false);
    }

    @Transactional
    public void createTag(String code, String name, String valueType, String definition,
                          long freshnessSeconds, String owner) {
        profiles.createTag(code, name, valueType, definition, freshnessSeconds, owner);
    }

    @Transactional
    public void activate(String code) {
        requireTransition(profiles.transitionTag(code, "DRAFT", "ACTIVE"));
    }

    @Transactional
    public void deprecate(String code) {
        requireTransition(profiles.transitionTag(code, "ACTIVE", "DEPRECATED"));
    }

    private void requireTransition(boolean changed) {
        if (!changed) throw new IllegalStateException("invalid tag lifecycle transition");
    }

    private String mask(String account) {
        if (account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
