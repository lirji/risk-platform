package com.lrj.risk.admin.profiles.adapter;

import java.util.List;
import java.util.Map;

import com.lrj.risk.admin.profiles.application.ProfilePort;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisJdbcProfileAdapter implements ProfilePort {
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;

    public RedisJdbcProfileAdapter(StringRedisTemplate redis, JdbcTemplate jdbc) {
        this.redis = redis;
        this.jdbc = jdbc;
    }

    @Override
    public OnlineFeatures onlineFeatures(String accountNo) {
        try {
            return new OnlineFeatures(redis.opsForHash().entries("feature:{" + accountNo + "}"), true);
        } catch (DataAccessException failure) {
            return new OnlineFeatures(Map.of(), false);
        }
    }

    @Override
    public List<Map<String, Object>> definitions(boolean activeFieldsOnly) {
        if (activeFieldsOnly) {
            return jdbc.queryForList("""
                    SELECT tag_code, tag_name, value_type, definition_text, freshness_seconds,
                           version_no, owner_id, status FROM tag_definition ORDER BY tag_code
                    """);
        }
        return jdbc.queryForList("SELECT * FROM tag_definition ORDER BY tag_code");
    }

    @Override
    public void createTag(String code, String name, String valueType, String definition,
                          long freshnessSeconds, String owner) {
        jdbc.update("""
                INSERT INTO tag_definition(tag_code, tag_name, value_type, definition_text,
                    freshness_seconds, version_no, owner_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, ?, 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, code, name, valueType, definition, freshnessSeconds, owner);
    }

    @Override
    public boolean transitionTag(String code, String fromStatus, String toStatus) {
        return jdbc.update("""
                UPDATE tag_definition SET status=?, updated_at=CURRENT_TIMESTAMP
                 WHERE tag_code=? AND status=?
                """, toStatus, code, fromStatus) == 1;
    }
}
