# 用户画像标签体系（基于交易数据）

> 本文档定义风控平台的用户画像标签口径，作为 `profiling-realtime` / `profiling-realtime-flink`
> 实时特征与离线标签作业的设计依据。标签命名与现有 `feature:{账号}` Redis Hash
> （`daily_amount` / `daily_count` / `txn_count_5m` / `device_new`）保持一致风格。

## 1. 标签分类体系

按加工方式分三层，是业界通用划分：

| 类型 | 含义 | 加工方式 | 示例 |
|------|------|----------|------|
| **事实标签** | 从原始交易直接统计，客观 | 聚合/计数 | 近 30 天交易笔数、累计交易额 |
| **规则标签** | 在事实标签上套业务阈值/区间 | 规则/分箱 | 高净值用户、活跃用户、夜间交易偏好 |
| **模型标签** | 算法/模型预测得出 | 机器学习 | 欺诈评分、流失概率、RFM 分群 |

按时效再分两类，决定计算引擎：

- **实时标签**：随每笔交易更新，毫秒级可读，用于在线拦截 → Flink / Redis（见 `FeatureAggregator`）。
- **离线标签**：T+1 批量计算，刻画长期画像 → Spark（见 `fraud-model-train` 同栈）。

## 2. 可用原始字段

标签均来源于 `TransactionMessage`（topic `txn-events`）：

| 字段 | 含义 | 可挖掘的标签维度 |
|------|------|------------------|
| `accountNo` | 账号（画像主键） | 聚合键 |
| `counterpartyAccount` | 交易对手账号 | 资金流向、对手集中度、关联网络 |
| `amount`（分） | 交易金额 | 消费能力、客单价、大额标签 |
| `channel` | 交易渠道 | 渠道偏好（线上/线下/APP） |
| `bizType` | 业务类型 | 消费品类偏好 |
| `deviceId` | 设备号 | 设备数、新设备、一机多账户 |
| `ip` | IP 地址 | 异地交易、IP 聚集 |
| `eventTime` | 交易时间 | 活跃时段、频次、RFM 的 R/F |

## 3. 标签字典

### 3.1 消费能力 / 价值类（离线为主）

| 标签 ID | 含义 | 口径 | 更新频率 |
|---------|------|------|----------|
| `avg_amount_30d` | 近 30 天客单价 | 金额求和 / 笔数 | T+1 |
| `total_amount_90d` | 近 90 天累计交易额 | 金额求和 | T+1 |
| `value_tier` | 价值分层 | 按累计额分箱：高/中/低净值 | T+1 |
| `max_amount_90d` | 近 90 天单笔最大金额 | max(amount) | T+1 |

### 3.2 消费行为 / 偏好类（离线）

| 标签 ID | 含义 | 口径 | 更新频率 |
|---------|------|------|----------|
| `top_biztype` | 偏好业务品类 | 按 `bizType` 计数取 TopN | T+1 |
| `top_channel` | 偏好渠道 | 按 `channel` 计数取 Top1 | T+1 |
| `active_hour_pref` | 活跃时段偏好 | 按 `eventTime` 小时分布取众数（早/晚/夜间） | T+1 |
| `night_txn_ratio` | 夜间交易占比 | 0–6 点交易笔数 / 总笔数 | T+1 |

### 3.3 活跃度 / 生命周期类（RFM，离线）

| 标签 ID | 含义 | 口径 | 更新频率 |
|---------|------|------|----------|
| `recency_days` | 最近一次交易距今天数（R） | now − max(eventTime) | T+1 |
| `frequency_30d` | 近 30 天交易频次（F） | count | T+1 |
| `monetary_30d` | 近 30 天交易金额（M） | sum(amount) | T+1 |
| `rfm_segment` | RFM 分群 | 高价值活跃 / 沉睡 / 流失预警 / 新客 | T+1 |
| `lifecycle_stage` | 生命周期阶段 | 新注册 / 成长 / 成熟 / 衰退 / 流失 | T+1 |

### 3.4 风控 / 反欺诈类（实时 + 离线）

| 标签 ID | 含义 | 口径 | 更新频率 |
|---------|------|------|----------|
| `daily_amount` | 当日累计金额 | 跨日重置累加（已实现） | 实时 |
| `daily_count` | 当日累计笔数 | 跨日重置累加（已实现） | 实时 |
| `txn_count_5m` | 5 分钟内交易笔数 | 滑动窗口计数（已实现，测速） | 实时 |
| `device_new` | 是否首次见到此设备 | SADD 去重（已实现） | 实时 |
| `device_count_90d` | 近 90 天关联设备数 | distinct(deviceId) | T+1 |
| `geo_anomaly` | 异地交易标签 | IP 归属地突变检测 | 实时/准实时 |
| `amount_volatility` | 金额波动稳定性 | 历史金额标准差/变异系数 | T+1 |
| `counterparty_concentration` | 对手集中度 | TopN 对手金额 / 总金额 | T+1 |
| `cross_bank_ratio` | 跨行交易占比 | 跨行笔数 / 总笔数（对手非本行） | T+1 |
| `net_fund_flow` | 资金净流入/流出 | sum(流入) − sum(流出) | T+1 |
| `fast_in_out_ratio` | 快进快出占比 | 收款后 N 分钟内转出的金额占比（洗钱/欺诈强信号） | T+1 |
| `round_amount_ratio` | 整数大额/拆分倾向 | 整数金额笔数占比（规避阈值信号） | T+1 |
| `fraud_score` | 欺诈评分 | 随机森林模型输出（对接 `fraud-model-train`） | T+1 / 近实时 |

### 3.5 关联 / 图计算类（离线，进阶）

| 标签 ID | 含义 | 口径 | 更新频率 |
|---------|------|------|----------|
| `fund_role` | 资金网络角色 | 图算法：中转节点 / 星型中心 | T+1 |
| `shared_device_group` | 共享设备团伙 | 同 `deviceId` 关联账号簇 | T+1 |
| `gang_risk_flag` | 团伙风险标记 | 关联网络欺诈密度 | T+1 |

## 4. 计算与存储分工

```
                  ┌─────────────── 实时链路 (毫秒级) ───────────────┐
txn-events ──▶ Flink FeatureAggregator ──▶ Redis feature:{账号}
 (Kafka)        daily_amount/count、txn_count_5m、device_new        │
                                                          反欺诈规则实时读取拦截
                  └──────────────────────────────────────────────┘

                  ┌─────────────── 离线链路 (T+1) ─────────────────┐
Hive 宽表 (MinIO/S3A) ─▶ Spark 批作业 ─▶ 画像宽表 dwd_cust_feature │
 + ES 标签 join         RFM、价值分层、偏好、模型评分、图标签  ─▶ ES 风险库
                                                          Kibana 画像查询 / 模型特征
                  └──────────────────────────────────────────────┘
```

- **实时标签**：沿用已实现的 Flink/Redis 算子，`feature:{账号}` Hash 直接扩展字段。
- **离线标签**：Spark 批作业读 **Hive 宽表（Metastore + MinIO/S3A 存算分离）JOIN ES 标签**，产出画像宽表 `risk_dw.dwd_cust_feature`，与 `fraud-model-train` / `rating-engine` 同技术栈。详见 [`HIVE-INTEGRATION-PLAN.md`](./HIVE-INTEGRATION-PLAN.md)。
- **存储**：离线宽表/训练样本落 Hive（数据在 MinIO/S3A），在线特征走 Redis（`feature:{账号}`），评级/风险结果与可视化走 ES + Kibana。

## 5. 离线画像宽表落地：`risk_dw.dwd_cust_feature`

上面的离线标签字典落到 `rating-engine` 实际读取的 Hive 宽表（EXTERNAL 表，数据在 MinIO/S3A）。
该表与 ES `cust-tags` 在 Spark 里 `JOIN on cust_id` 后喂给评级模型，产出 A/B/C/D 评级写 ES 风险库。
建表与灌数见 `HiveSeedJob` + `scripts/setup-hive.sh`（详见 [`HIVE-INTEGRATION-PLAN.md`](./HIVE-INTEGRATION-PLAN.md) §4/§5）。

| 宽表列 | 对应标签字典 | 类别 |
|--------|-------------|------|
| `cust_id` | 画像主键 | 聚合键 |
| `txn_cnt_30d` / `txn_cnt_90d` | `frequency_30d` 及其 90d 口径 | RFM-F |
| `txn_amt_30d` / `txn_amt_90d` | `monetary_30d` / `total_amount_90d` | RFM-M |
| `avg_amt` / `max_amt` | `avg_amount_30d` / `max_amount_90d` | RFM-M |
| `recency_days` | `recency_days` | RFM-R |
| `night_txn_ratio` | `night_txn_ratio` | 行为偏好 |
| `cross_bank_ratio` | `cross_bank_ratio` | 资金/关系 |
| `counterparty_cnt` | `counterparty_concentration`（数量口径） | 关系网络 |
| `device_cnt` | `device_count_90d` | 设备 |
| `account_age_days` | `lifecycle_stage`（账龄口径） | 生命周期 |

> 训练样本宽表 `risk_dw.dwd_fraud_train` 列名严格对齐在线 `ModelScorer`
> （`amount/daily_amount/daily_count/hour/cross_bank/device_new` + `label`），保证 training-serving 一致。

## 6. 后续落地 TODO

- [ ] 在 `profiling-realtime-flink` 扩展 `geo_anomaly`、`device_count_90d` 等实时算子
- [x] 定义离线画像宽表 schema → `risk_dw.dwd_cust_feature`（见 §5 / HIVE-INTEGRATION-PLAN.md）
- [ ] 实现 `HiveSeedJob` + `setup-hive.sh`，建表灌数（Hive Metastore + MinIO/S3A）
- [ ] 改造 `RatingEngineJob`：Hive 宽表 JOIN ES 标签产出评级
- [ ] 接入 `fraud-model-train` 输出，将 `fraud_score` 回写画像
- [ ] Kibana 建画像查询面板（对齐 README 的案件调查能力）
