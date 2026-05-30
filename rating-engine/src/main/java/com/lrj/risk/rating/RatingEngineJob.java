package com.lrj.risk.rating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lrj.risk.rating.config.ConfigRepository;
import com.lrj.risk.rating.config.RatingModel;
import com.lrj.risk.rating.config.RatingTask;
import com.lrj.risk.rating.es.EsClient;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;

/**
 * 离线评分评级引擎 (架构 A 核心, 任务驱动)。
 *
 * <p>流程 (对应架构图 Spark 引擎 4 步):
 * <ol>
 *   <li>自主读取 MySQL: 领一个 PENDING 任务 + 加载其模型(评分规则+评级阈值)</li>
 *   <li>生成并拉取 ES 标签数据 (source_index)</li>
 *   <li>Spark 分布式应用评分规则到标签 → 总分 + 评级 (模型广播到 executor)</li>
 *   <li>评分结果写 es 风险库 (target_index), 任务置 DONE</li>
 * </ol>
 *
 * <p>环境变量: MYSQL_URL / MYSQL_USER / MYSQL_PWD / ES_URL。
 */
public class RatingEngineJob {

    public static void main(String[] args) {
        String mysqlUrl = System.getenv().getOrDefault("MYSQL_URL",
                "jdbc:mysql://127.0.0.1:13306/risk_platform?useSSL=false&allowPublicKeyRetrieval=true");
        String mysqlUser = System.getenv().getOrDefault("MYSQL_USER", "root");
        String mysqlPwd = System.getenv().getOrDefault("MYSQL_PWD", "root123");
        String esUrl = System.getenv().getOrDefault("ES_URL", "http://localhost:9200");

        ConfigRepository repo = new ConfigRepository(mysqlUrl, mysqlUser, mysqlPwd);
        EsClient es = new EsClient(esUrl);

        // 步骤1: 领任务 + 加载模型
        Optional<RatingTask> opt = repo.nextPendingTask();
        if (opt.isEmpty()) {
            System.out.println("[rating-engine] 无 PENDING 任务, 退出");
            return;
        }
        RatingTask task = opt.get();
        RatingModel model = repo.loadModel(task.getModelCode());
        System.out.printf("[rating-engine] 领到任务 %s, 模型 %s%n", task.getTaskCode(), model.getModelCode());
        repo.updateTaskStatus(task.getId(), "RUNNING");

        SparkSession spark = SparkSession.builder()
                .appName("rating-engine-" + task.getTaskCode())
                .master("local[*]")
                .config("spark.ui.enabled", "false")
                .getOrCreate();
        spark.sparkContext().setLogLevel("WARN");
        JavaSparkContext jsc = JavaSparkContext.fromSparkContext(spark.sparkContext());

        try {
            // 步骤2: 拉 ES 标签
            List<Map<String, Object>> tagRows = es.fetchAll(task.getSourceIndex());
            System.out.printf("[rating-engine] 拉取标签 %d 条%n", tagRows.size());
            if (tagRows.isEmpty()) {
                repo.updateTaskStatus(task.getId(), "DONE");
                return;
            }

            // 步骤3: Spark 分布式评分评级 (模型随闭包序列化分发)
            JavaRDD<Map<String, Object>> results = jsc.parallelize(tagRows).map(row -> {
                Map<String, Double> tags = new HashMap<>();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    if (e.getValue() instanceof Number n) {
                        tags.put(e.getKey(), n.doubleValue());
                    }
                }
                RatingModel.ScoreResult r = model.score(tags);
                Map<String, Object> out = new HashMap<>();
                out.put("_id", String.valueOf(row.getOrDefault("cust_id", row.get("_id"))));
                out.put("cust_id", row.get("cust_id"));
                out.put("model_code", model.getModelCode());
                out.put("score", r.score());
                out.put("grade", r.grade());
                out.put("hit_rules", r.hitRules());
                return out;
            });

            List<Map<String, Object>> riskDocs = new ArrayList<>(results.collect());

            // 步骤4: 写 es 风险库 + 任务完成
            es.bulkIndex(task.getTargetIndex(), riskDocs);
            repo.updateTaskStatus(task.getId(), "DONE");
            System.out.printf("[rating-engine] 完成: %d 个客户评级写入 %s%n",
                    riskDocs.size(), task.getTargetIndex());
            riskDocs.forEach(d -> System.out.printf("  %s → 分%s 级%s 命中%s%n",
                    d.get("cust_id"), d.get("score"), d.get("grade"), d.get("hit_rules")));
        } catch (RuntimeException e) {
            repo.updateTaskStatus(task.getId(), "FAILED");
            throw e;
        } finally {
            spark.stop();
        }
    }
}
