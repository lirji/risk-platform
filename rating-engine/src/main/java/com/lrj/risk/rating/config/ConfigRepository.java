package com.lrj.risk.rating.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MySQL 配置中心读取 (架构 A "配置即数据"): 任务、评分规则、评级阈值。
 * 用普通 JDBC, 引擎运行时自主读取, 改库不改代码。
 */
public class ConfigRepository {

    private final String url;
    private final String user;
    private final String password;

    public ConfigRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** 取最早一个 PENDING 任务 (模拟引擎"领任务")。 */
    public Optional<RatingTask> nextPendingTask() {
        String sql = "SELECT id, task_code, model_code, source_index, target_index " +
                "FROM t_rating_task WHERE status='PENDING' ORDER BY id LIMIT 1";
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return Optional.of(new RatingTask(
                        rs.getLong("id"), rs.getString("task_code"), rs.getString("model_code"),
                        rs.getString("source_index"), rs.getString("target_index")));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("读取任务失败", e);
        }
    }

    /** 按模型编码加载完整模型 (规则 + 评级阈值)。 */
    public RatingModel loadModel(String modelCode) {
        List<ScoreRule> rules = new ArrayList<>();
        Map<Integer, String> grades = new LinkedHashMap<>();
        String ruleSql = "SELECT rule_code, tag_field, operator, threshold, score " +
                "FROM t_score_rule WHERE model_code=? AND enabled=1";
        String gradeSql = "SELECT grade, min_score FROM t_rating_grade WHERE model_code=?";
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(ruleSql)) {
                ps.setString(1, modelCode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rules.add(new ScoreRule(rs.getString("rule_code"), rs.getString("tag_field"),
                                rs.getString("operator"), rs.getDouble("threshold"), rs.getInt("score")));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(gradeSql)) {
                ps.setString(1, modelCode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        grades.put(rs.getInt("min_score"), rs.getString("grade"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("加载模型失败: " + modelCode, e);
        }
        return new RatingModel(modelCode, rules, grades);
    }

    /** 更新任务状态。 */
    public void updateTaskStatus(long taskId, String status) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("UPDATE t_rating_task SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setLong(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新任务状态失败", e);
        }
    }
}
