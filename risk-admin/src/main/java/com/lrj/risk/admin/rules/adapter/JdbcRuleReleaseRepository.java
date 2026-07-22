package com.lrj.risk.admin.rules.adapter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.risk.admin.rules.domain.RuleRelease;
import com.lrj.risk.admin.rules.domain.RuleReleaseStatus;
import com.lrj.risk.admin.rules.application.port.RuleReleaseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRuleReleaseRepository implements RuleReleaseRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcRuleReleaseRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public int nextVersion(String ruleCode) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM rule_release WHERE rule_code = ?",
                Integer.class, ruleCode);
        return value == null ? 1 : value;
    }

    public void insert(RuleRelease release) {
        jdbc.update("""
                INSERT INTO rule_release(release_id, rule_code, rule_name, version_no, status, drl,
                                         checksum, author_id, reviewer_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, release.releaseId(), release.ruleCode(), release.ruleName(), release.version(),
                release.status().name(), release.drl(), release.checksum(), release.authorId(),
                release.reviewerId(), Timestamp.from(release.createdAt()), Timestamp.from(release.updatedAt()));
    }

    public void update(RuleRelease release) {
        jdbc.update("""
                UPDATE rule_release SET status = ?, reviewer_id = ?, updated_at = ? WHERE release_id = ?
                """, release.status().name(), release.reviewerId(), Timestamp.from(release.updatedAt()),
                release.releaseId());
    }

    public Optional<RuleRelease> find(String releaseId) {
        return jdbc.query("SELECT * FROM rule_release WHERE release_id = ?", (rs, row) -> new RuleRelease(
                rs.getString("release_id"), rs.getString("rule_code"), rs.getString("rule_name"),
                rs.getInt("version_no"), rs.getString("drl"), rs.getString("checksum"),
                rs.getString("author_id"), RuleReleaseStatus.valueOf(rs.getString("status")),
                rs.getString("reviewer_id"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), releaseId).stream().findFirst();
    }

    public List<RuleRelease> list() {
        return jdbc.query("SELECT * FROM rule_release ORDER BY created_at DESC", (rs, row) -> new RuleRelease(
                rs.getString("release_id"), rs.getString("rule_code"), rs.getString("rule_name"),
                rs.getInt("version_no"), rs.getString("drl"), rs.getString("checksum"),
                rs.getString("author_id"), RuleReleaseStatus.valueOf(rs.getString("status")),
                rs.getString("reviewer_id"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
    }

    public void activateBinding(String sourceId, String releaseId, List<String> ruleSets,
                                int rolloutPercentage, String shadowReleaseId, Instant now) {
        String groups = json(ruleSets);
        int updated = jdbc.update("""
                UPDATE source_rule_binding
                   SET previous_release_id = active_release_id, active_release_id = ?, rule_sets_json = ?,
                       rollout_percentage = ?, shadow_release_id = ?, updated_at = ?
                 WHERE source_id = ?
                """, releaseId, groups, rolloutPercentage, shadowReleaseId, Timestamp.from(now), sourceId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO source_rule_binding(source_id, active_release_id, previous_release_id,
                        rule_sets_json, rollout_percentage, shadow_release_id, fail_safe_action, updated_at)
                    VALUES (?, ?, NULL, ?, ?, ?, 'CHALLENGE', ?)
                    """, sourceId, releaseId, groups, rolloutPercentage, shadowReleaseId, Timestamp.from(now));
        }
    }

    public Optional<Binding> binding(String sourceId) {
        return jdbc.query("SELECT * FROM source_rule_binding WHERE source_id=?", (rs, row) -> new Binding(
                rs.getString("source_id"), rs.getString("active_release_id"),
                rs.getString("previous_release_id"), groups(rs.getString("rule_sets_json")),
                rs.getInt("rollout_percentage"), rs.getString("shadow_release_id")), sourceId)
                .stream().findFirst();
    }

    @Override
    public List<Binding> listBindings() {
        return jdbc.query("SELECT * FROM source_rule_binding ORDER BY source_id", (rs, row) -> new Binding(
                rs.getString("source_id"), rs.getString("active_release_id"),
                rs.getString("previous_release_id"), groups(rs.getString("rule_sets_json")),
                rs.getInt("rollout_percentage"), rs.getString("shadow_release_id")));
    }

    public void rollbackBinding(String sourceId, Instant now) {
        int updated = jdbc.update("""
                UPDATE source_rule_binding
                   SET active_release_id=previous_release_id, previous_release_id=active_release_id,
                       rollout_percentage=100, shadow_release_id=NULL, updated_at=?
                 WHERE source_id=? AND previous_release_id IS NOT NULL
                """, Timestamp.from(now), sourceId);
        if (updated != 1) throw new IllegalStateException("source has no previous rule release");
    }

    private List<String> groups(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("invalid persisted rule-set binding", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
