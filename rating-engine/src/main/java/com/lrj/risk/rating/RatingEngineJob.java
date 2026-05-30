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
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * 离线评分评级引擎 (架构 A 核心, 任务驱动)。
 *
 * <p>流程 (对应架构图 Spark 引擎 4 步):
 * <ol>
 *   <li>自主读取 MySQL: 领一个 PENDING 任务 + 加载其模型(评分规则+评级阈值)</li>
 *   <li>双输入源跨源关联: Hive 离线宽表(历史聚合标签) JOIN ES(行为标签) on cust_id</li>
 *   <li>Spark 分布式应用评分规则到标签 → 总分 + 评级 (模型广播到 executor)</li>
 *   <li>评分结果写 es 风险库 (target_index), 任务置 DONE</li>
 * </ol>
 *
 * <p>Hive 宽表 {@code risk_dw.dwd_cust_feature} 提供 amount_90d/txn_cnt_90d/counterparty 等历史聚合;
 * ES {@code cust-tags} 提供 avg_balance/night_ratio 等行为标签; join 后字段齐全喂给评分模型。
 *
 * <p>环境变量: MYSQL_URL / MYSQL_USER / MYSQL_PWD / ES_URL / HIVE_METASTORE_URIS / S3_ENDPOINT。
 */
public class RatingEngineJob {

    /** Hive 离线画像宽表 (由 HiveSeedJob 建/灌, 数据在 MinIO/S3A)。 */
    private static final String HIVE_FEATURE_TABLE = "risk_dw.dwd_cust_feature";

    public static void main(String[] args) {
        String mysqlUrl = System.getenv().getOrDefault("MYSQL_URL",
                "jdbc:mysql://127.0.0.1:13307/risk_platform?useSSL=false&allowPublicKeyRetrieval=true");
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

        SparkSession spark = SparkSessions.builder("rating-engine-" + task.getTaskCode());

        try {
            // 步骤2: Hive 离线宽表 JOIN ES 行为标签 (跨源关联)
            Dataset<Row> hiveFeatures = spark.table(HIVE_FEATURE_TABLE);
            Dataset<Row> esTags = esTagsDataFrame(spark, es.fetchAll(task.getSourceIndex()));
            Dataset<Row> joined = hiveFeatures.join(esTags, "cust_id");
            System.out.printf("[rating-engine] Hive 宽表 %d 行 JOIN ES 标签 %d 行 → %d 行%n",
                    hiveFeatures.count(), esTags.count(), joined.count());
            if (joined.isEmpty()) {
                repo.updateTaskStatus(task.getId(), "DONE");
                return;
            }

            // 步骤3: Spark 分布式评分评级 (模型随闭包序列化分发)
            JavaRDD<Map<String, Object>> results = joined.toJavaRDD().map(row -> {
                Map<String, Double> tags = new HashMap<>();
                String custId = row.getAs("cust_id");
                for (StructField f : row.schema().fields()) {
                    Object v = row.getAs(f.name());
                    if (v instanceof Number n) {
                        tags.put(f.name(), n.doubleValue());
                    }
                }
                RatingModel.ScoreResult r = model.score(tags);
                Map<String, Object> out = new HashMap<>();
                out.put("_id", custId);
                out.put("cust_id", custId);
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

    /**
     * 把 ES 标签行转成 DataFrame 以便与 Hive 宽表 join。
     * ES cust-tags 提供 cust_id(keyword) + 行为标签(数值), 数值统一转 double。
     */
    private static Dataset<Row> esTagsDataFrame(SparkSession spark, List<Map<String, Object>> esRows) {
        // 收集除 cust_id 外的全部数值字段名 (各行字段一致)
        List<String> numericFields = new ArrayList<>();
        for (Map<String, Object> row : esRows) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (!"cust_id".equals(e.getKey()) && e.getValue() instanceof Number
                        && !numericFields.contains(e.getKey())) {
                    numericFields.add(e.getKey());
                }
            }
        }
        List<StructField> fields = new ArrayList<>();
        fields.add(new StructField("cust_id", DataTypes.StringType, false, Metadata.empty()));
        for (String name : numericFields) {
            fields.add(new StructField(name, DataTypes.DoubleType, true, Metadata.empty()));
        }
        StructType schema = new StructType(fields.toArray(new StructField[0]));

        List<Row> rows = new ArrayList<>(esRows.size());
        for (Map<String, Object> row : esRows) {
            Object[] vals = new Object[numericFields.size() + 1];
            vals[0] = String.valueOf(row.get("cust_id"));
            for (int i = 0; i < numericFields.size(); i++) {
                Object v = row.get(numericFields.get(i));
                vals[i + 1] = v instanceof Number n ? n.doubleValue() : null;
            }
            rows.add(RowFactory.create(vals));
        }
        return spark.createDataFrame(rows, schema);
    }
}
