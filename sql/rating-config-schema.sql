-- 离线评分评级引擎的"配置中心" (架构 A 核心: 配置即数据)
-- 模型/评分规则/评级阈值/任务全部存 MySQL, rating-engine 运行时自主读取, 改库不改代码。
-- 应用: scripts/setup-rating.sh 自动灌入; 库 risk_platform。

CREATE TABLE IF NOT EXISTS t_rating_task (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_code    VARCHAR(64)  NOT NULL UNIQUE COMMENT '任务编码',
    model_code   VARCHAR(64)  NOT NULL        COMMENT '关联模型(评分规则集)编码',
    source_index VARCHAR(128) NOT NULL        COMMENT 'ES 标签数据源索引',
    target_index VARCHAR(128) NOT NULL        COMMENT 'es 风险库目标索引',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='评级任务: Web 平台创建, 引擎读 PENDING 任务执行';

CREATE TABLE IF NOT EXISTS t_score_rule (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_code  VARCHAR(64)  NOT NULL COMMENT '所属模型编码',
    rule_code   VARCHAR(64)  NOT NULL COMMENT '规则编码(命中码)',
    tag_field   VARCHAR(64)  NOT NULL COMMENT '作用的标签字段',
    operator    VARCHAR(8)   NOT NULL COMMENT '比较符: GT/GE/LT/LE/EQ',
    threshold   DOUBLE       NOT NULL COMMENT '阈值',
    score       INT          NOT NULL COMMENT '命中加分',
    enabled     TINYINT      NOT NULL DEFAULT 1,
    UNIQUE KEY uk_model_rule (model_code, rule_code)
) COMMENT='评分规则: 标签字段 比较符 阈值 → 命中加分; 引擎对每个客户累加得总分';

CREATE TABLE IF NOT EXISTS t_rating_grade (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_code  VARCHAR(64) NOT NULL COMMENT '所属模型编码',
    grade       VARCHAR(8)  NOT NULL COMMENT '评级: A/B/C/D',
    min_score   INT         NOT NULL COMMENT '该等级分数下限(含)',
    UNIQUE KEY uk_model_grade (model_code, grade)
) COMMENT='评级阈值: 总分映射到风险等级';

-- ===== 种子数据: 模型 RISK_M1 =====
INSERT INTO t_score_rule (model_code, rule_code, tag_field, operator, threshold, score) VALUES
 ('RISK_M1', 'HIGH_AMOUNT_90D',  'amount_90d',   'GT', 1000000, 30),
 ('RISK_M1', 'HIGH_FREQ_90D',    'txn_cnt_90d',  'GT', 200,     20),
 ('RISK_M1', 'MANY_COUNTERPART', 'counterparty', 'GT', 50,      20),
 ('RISK_M1', 'LOW_BALANCE',      'avg_balance',  'LT', 1000,    15),
 ('RISK_M1', 'NIGHT_ACTIVE',     'night_ratio',  'GT', 0.5,     15)
ON DUPLICATE KEY UPDATE score=VALUES(score);

INSERT INTO t_rating_grade (model_code, grade, min_score) VALUES
 ('RISK_M1', 'D', 70),
 ('RISK_M1', 'C', 45),
 ('RISK_M1', 'B', 20),
 ('RISK_M1', 'A', 0)
ON DUPLICATE KEY UPDATE min_score=VALUES(min_score);

INSERT INTO t_rating_task (task_code, model_code, source_index, target_index, status) VALUES
 ('T_DAILY_RATING', 'RISK_M1', 'cust-tags', 'es-risk-store', 'PENDING')
ON DUPLICATE KEY UPDATE status='PENDING';
