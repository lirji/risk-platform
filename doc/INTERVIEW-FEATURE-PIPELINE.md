# 面试速记卡：在线/离线特征计算（Kafka→Redis & 在线 vs 离线）

> 历史面试说明：本文保留用于讲解概念；当前轻量与 Flink 实现已共享事件时间聚合语义，并补充 checkpoint、TTL、幂等和 DLT。以根目录 `README.md` 为准。

> 配套详解见 [REQUEST-FLOW.md §4](REQUEST-FLOW.md)（扇出）、[ARCHITECTURE-COMPARISON.md §6/§7](ARCHITECTURE-COMPARISON.md)（标签分层 + 引擎落位）。

## 🎯 主线

> **在线对交易流做增量窗口计算（毫秒落 Redis），离线对历史全量做批量聚合（T+1 落 Hive/ES）；两者特征和形态都不同，唯一要对齐的是喂模型的那组特征口径。**

## Q1：Kafka 之后特征怎么算进 Redis

`fraud-gateway` 决策后发 `txn-events`(key=账号) → 两个**等价实现二选一**消费，差别在**状态存哪**：

**实现 A · 轻量 consumer（`profiling-realtime`）— 状态在 Redis**
- `@KafkaListener(group=realtime-feature)` → `RealtimeFeatureService`
- 全靠 **Redis Lua 原子脚本**（读-改-写防竞态）：
  - `daily_amount/count`：DAILY_LUA，跨日清零 + `HINCRBY`
  - `txn_count_5m`：VELOCITY_LUA，ZSet `ZADD`→`ZREMRANGEBYSCORE`剔窗外→`ZCARD`
  - `device_new`：`SADD` 返回 1 = 首见设备
- **Redis = 计算 + 存储**

**实现 B · Flink（`profiling-realtime-flink`）— 状态在 Flink**
- `KafkaSource → map解析 → keyBy(账号) → FeatureAggregator(KeyedProcessFunction)`
- 中间值用 **Flink 托管状态**：`ValueState(dailyAmount/Count/Date)` + `ListState(recentTs)`
- 增量更新后 `jedis.hset("feature:{账号}")`
- **Flink 管状态(可 checkpoint)，Redis 仅在线服务出口**

| | consumer 版 | Flink 版 |
|---|---|---|
| 状态在哪 | Redis(Lua) | Flink(ValueState/ListState) |
| Redis 角色 | 计算+存储 | 仅出口 |
| 容错 | Redis 持久化 | checkpoint 精确一次 |

> 读侧统一：gateway 下一笔 `HGETALL feature:{账号}` 一次取齐。

## Q2：在线 vs 离线处理的点一样吗 —— 不一样

| | 在线(Flink/Redis) | 离线(Spark) |
|---|---|---|
| 模块 | `profiling-realtime(-flink)` | `rating-engine`/`fraud-model-train` |
| 特征 | daily_amount、txn_count_5m、device_new（**窗口/频次**） | amount_90d、txn_cnt_90d、night_ratio（**历史聚合/挖掘**） |
| 源 | Kafka 交易流 | Hive 宽表 JOIN ES 标签 |
| 形态 | **逐笔增量**更新窗口 | **全量批量**聚合、可重算 |
| 时效 | 毫秒(T+ε) | T+1 |

**为什么分这么开**：实时窗口类 Spark 批处理延迟扛不住 50ms；历史挖掘类流计算算不动全量 → 标签分层，互补非替代。

## ⚠️ 唯一必须对齐：training-serving 一致性

喂模型的特征（`amount/daily_amount/daily_count/hour/cross_bank/device_new`）**口径必须线上线下一致**：
- 例：金额训练用"元" → 在线 ModelScorer 也得 `/100` 分换元；窗口定义两侧同口径
- 否则 **training-serving skew**：模型线下好、线上乱

## 🏁 收尾

> 在线/离线处理形态和特征都不同，覆盖标签体系不同时效区间，互补而非替代；唯一要严格对齐的是喂模型的那组特征口径。

## 关键词

`Kafka扇出` `Lua原子脚本(读改写防竞态)` `ZSet滑动窗口` `Flink托管状态(ValueState/ListState)` `checkpoint精确一次` `Redis计算vs出口` `增量流式 vs 全量批` `标签分层` `T+ε` `training-serving一致性`
