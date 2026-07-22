# 面试速记卡：规则 + 模型双轨决策

> 配套详解见 [REQUEST-FLOW.md §3](REQUEST-FLOW.md)（引擎内部）、[RANDOM-FOREST-THEORY.md](RANDOM-FOREST-THEORY.md)（模型理论）。

## 🎯 主线

> **规则管"可解释的硬约束"，模型管"难枚举的软模式"；模型分进引擎前算好塞进 Fact，规则只读不算，两条轨在同一引擎里按"取最高风险等级"聚合。**

## 为什么要双轨（一句话回答价值）

- **纯规则**：可解释、可审计、改得快，但写不完所有欺诈模式，靠人想
- **纯模型**：能抓复杂/未知模式，但黑盒、不可解释、不好临时干预
- **双轨**：规则兜可解释底线（黑名单、大额、合规阈值），模型补模式发现（随机森林欺诈概率），互补

## 怎么协同（关键设计点）

1. **模型分进引擎前算好**：`ModelScorer.score()` 在 `FraudEngineService` 里 `session.insert` 之前算出 `fraudScore` 塞进 `RiskAssessment` Fact —— **规则只读 `fraudScore`，不在规则里调模型**（保证引擎内零 IO、规则纯内存）
2. **模型也是"一组规则"**：DRL 的 `model` agenda-group 里写阈值规则
   - `fraudScore > 0.9` → REJECT（`MODEL_HIGH_RISK`）
   - `0.6 < fraudScore ≤ 0.9` → HIGH（`MODEL_MID_RISK`）
   - 好处：模型分和规则用**同一套裁决/聚合机制**，模型阈值也能像规则一样调
3. **聚合 = 取最高等级**：`RiskAssessment.escalate` 只升不降，规则命中和模型命中谁严重听谁的，顺序无关

## 训练 → 上线链路

```
fraud-model-train (Spark MLlib 随机森林) → pmml-sparkml 导出 PMML
   → fraud-engine 资源 model/fraud-rf.pmml
   → ModelScorer 启动加载(jpmml-evaluator) → 进程内逐笔推理
```

- 进程内推理（不独立部署成服务）：省一跳 RPC，保毫秒 SLA
- 模型失败降级：加载/评分异常 → `fraudScore=0` → 退化纯规则，不阻断

## ⚠️ 易追问点

| 追问 | 答 |
|------|-----|
| 规则和模型冲突谁说了算？ | 不"二选一"，**取最高风险等级**聚合，都命中就听最严的 |
| 模型为何不在规则里直接调？ | 那样引擎里就有 IO/重计算了；改成"前置算好塞 Fact"，规则只读，保零 IO |
| 模型特征和在线特征关系？ | 模型特征来自 txn + Redis 特征快照，口径须与训练一致（training-serving，见 [INTERVIEW-FEATURE-PIPELINE.md](INTERVIEW-FEATURE-PIPELINE.md)） |
| 怎么调模型激进程度？ | 改 `model` 组阈值（0.9/0.6）即可，无需重训；和规则一样可热发布 |

## 🏁 收尾

> 双轨不是规则 vs 模型二选一，而是**把模型分当成一种特殊的"规则输入"**：前置算好、规则只读、统一聚合。既要模型的模式发现力，又保规则的可解释与可干预。

## 关键词

`规则可解释+模型抓模式` `fraudScore前置算好塞Fact` `规则只读不算(保零IO)` `model agenda-group阈值规则` `escalate取最高等级` `PMML+jpmml进程内推理` `模型失败降级纯规则` `阈值可热发布`
