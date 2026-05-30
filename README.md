# risk-platform

实时交易反欺诈 + 客户画像平台。完整规划见 [`PLAN.md`](PLAN.md)，能力清单见 [`CAPABILITIES.md`](CAPABILITIES.md)。

当前为 **最小可跑核心 (M1+M4 雏形)**：一笔交易经接入层 → 拉特征 → Drools 规则引擎 → 返回风险等级，端到端跑通。

## 模块

| 模块 | 职责 |
|------|------|
| `common-feature` | 共享层：`FeatureSnapshot` + `FeatureClient`/`RedisFeatureClient`(降级容错) + Kafka 消息体 `TransactionMessage` |
| `fraud-engine` | Drools 规则引擎：场景规则集(agenda-group) + 来源绑定 + 决策聚合 |
| `fraud-gateway` | 同步决策接入层 (Spring Boot, 8082)：拉特征 + 调引擎 + 返回风险等级 + 决策后异步发 Kafka |
| `profiling-realtime` | 实时特征计算：消费 Kafka 交易流 → Redis 原子算窗口特征(当日累计/笔数、新设备) → 写回 `feature:{账号}` |
| `fraud-model-train` | 离线训练：Spark MLlib 随机森林 → 评估(AUC) → 导出 PMML 到 fraud-engine 资源目录 |
| `profiling-realtime-flink` | 实时特征的 **Flink 平替**：DataStream 消费 Kafka → 键控状态聚合 → 写 Redis（与 profiling-realtime 二选一）|
| `logstash/` (配置) | 交易检索链路：Logstash 消费同一交易流 → 清洗+脱敏 → ES 时间滚动索引 |

fraud-engine 内 `ModelScorer` 用 jpmml-evaluator 加载该 PMML 做线上推理，`fraudScore` 进 Drools `model` 规则集做**双轨决策**(规则 + 模型分，§4.3)。

### 数据流闭环 (§2.2 / §2.4)

```
交易 → fraud-gateway 同步决策(返回银行) ──异步──→ Kafka(txn-events, 按账号分区)
                                                      │ (多 consumer group 并行)
                          ┌───────────────────────────┼───────────────────────────┐
                          ▼                                                         ▼
        profiling-realtime(group realtime-feature)             Logstash(group logstash-es)
          → 算实时特征 → Redis feature:{账号}                    → 清洗+脱敏 → ES txn-YYYY.MM.dd
                          ▼
        下一笔评估被 fraud-gateway 读到 → 规则命中
```

> `drools-demo/`（上层目录）为规则引擎能力参考脚手架，本平台的 fraud-engine 借鉴其模式但独立实现。

## 技术基线

Java 21 / Spring Boot 3.3.5 / Drools 8.44.2.Final（与 drools-demo 同验证组合）。

## 快速开始

```bash
# 1) (可选) 起本地基础设施: MySQL + Redis + Kafka + ES
docker compose up -d

# 2) 构建 + 跑规则测试 (真实编译 DRL 并触发规则)
./mvnw clean package

# 3) 启动实时特征消费者 + 接入层 (Redis/Kafka 没起也能跑, 特征降级为空快照、发 Kafka 静默失败)
java -jar profiling-realtime/target/profiling-realtime-0.0.1-SNAPSHOT.jar &
java -jar fraud-gateway/target/fraud-gateway-0.0.1-SNAPSHOT.jar
```

### 看数据流闭环

对同一账号连发 4 笔 4 万元交易：前 3 笔 daily_amount 累积到 12 万 (consumer 异步写 Redis)，
第 4 笔评估时读到 >10 万阈值 → `HIGH` / `DAILY_AMOUNT_EXCEEDED`。

```bash
for i in 1 2 3 4; do
  curl -s -X POST localhost:8082/risk/evaluate -H "Content-Type: application/json" \
    -d '{"sourceId":"MOBILE_TRANSFER","bizType":"TRANSFER","accountNo":"ACC200","amount":4000000}'; echo
  sleep 1.5
done
# 查实时特征: docker exec risk-redis redis-cli HGETALL feature:ACC200
```

## 试一笔

```bash
# 大额转账 → HIGH / REVIEW, 命中 LARGE_TRANSFER
curl -s -X POST localhost:8082/risk/evaluate -H "Content-Type: application/json" \
  -d '{"sourceId":"MOBILE_TRANSFER","channel":"MOBILE","bizType":"TRANSFER","accountNo":"ACC002","amount":6000000}'
# => {"riskLevel":"HIGH","action":"REVIEW","fraudScore":0.0,"hitRules":["LARGE_TRANSFER"],"costMs":11}
```

### 让特征类规则生效 (需 Redis)

特征以 Hash 存于 `feature:{账号}`：

```bash
docker exec -it risk-redis redis-cli HSET feature:ACC003 blacklist true
# 之后对 ACC003 的任意交易 → REJECT, 命中 BLACKLIST_ACCOUNT
```

支持的特征键：`blacklist` / `device_blacklist` / `daily_amount`(分) / `device_new`。

## 现有规则 (`fraud-engine/.../rules/fraud/fraud-rules.drl`)

| 规则集(agenda-group) | 规则 | 触发 → 等级 |
|------|------|------|
| blacklist | 黑名单账户 / 黑名单设备 | → REJECT |
| threshold | 大额转账(>5万) / 当日累计超限(>10万) / 新设备中额(>1万) / 短时高频(5分钟≥5笔) | → HIGH / HIGH / MEDIUM / HIGH |
| threshold (决策表) | 金额分档矩阵 (渠道×金额, `amount-tier.csv` 业务方可维护) | → HIGH |
| model | 模型分 >0.9 / 0.6~0.9 (随机森林 fraudScore) | → REJECT / HIGH |

决策聚合：取所有命中规则的最高等级；等级 → 动作映射 LOW=ALLOW / MEDIUM=CHALLENGE / HIGH=REVIEW / REJECT=REJECT。

## 模型层 (M6: Spark 随机森林 + PMML 双轨)

```bash
# 训练 (Spark MLlib → 评估 AUC → 导出 PMML 到 fraud-engine 资源)
./mvnw -q -pl fraud-model-train compile && ./mvnw -q -pl fraud-model-train exec:exec
# => [训练完成] 样本=5000, AUC=0.74; 导出 fraud-engine/.../model/fraud-rf.pmml
```

线上：fraud-engine 启动加载 PMML，每笔交易算 `fraudScore`(随机森林欺诈概率) 塞进 Drools，
与规则共同裁决。大额跨行交易示例 → `fraudScore≈0.66` 命中 `MODEL_MID_RISK` + `LARGE_TRANSFER`，聚合 HIGH。

> 技术栈：**Spark 4.0 训练 + pmml-sparkml 3.2.10 导出 + jpmml-evaluator 推理**（Java 21 原生，避开 MLeap）。
> 合成数据欺诈率偏高(仅演示训练→服务管道)，生产用 Hive 真实案件标签 + 处理样本不平衡。

## 交易检索 (Logstash → ES, §2.5 / §2.6)

起完 docker (含 logstash) 后, 先应用索引模板再发交易:

```bash
./scripts/setup-es.sh                     # 应用 txn-* 索引模板 (mapping 精简)
# 发几笔交易 (用 16 位卡号能看清掩码效果)
curl -s -X POST localhost:8082/risk/evaluate -H "Content-Type: application/json" \
  -d '{"sourceId":"MOBILE_TRANSFER","bizType":"TRANSFER","accountNo":"6225880212340001","counterpartyAccount":"6217000099887766","amount":4500000,"deviceId":"dev-abc","ip":"10.2.3.4"}'

curl -s "localhost:9200/_cat/indices/txn-*?v"          # 看索引
curl -s "localhost:9200/txn-*/_search?pretty&size=1"   # 看脱敏后的文档
```

落 ES 的字段经过：时间标准化(`@timestamp`)、金额分→元(`amount_yuan`)、**账号掩码**(`account_masked`=前6后4)、**HMAC 一致性哈希**(`acct_hash`，同账号→同值可聚合、明文已移除)。索引按日滚动 `txn-YYYY.MM.dd`，mapping 见 `es-init/txn-index-template.json`（生产再叠加 ILM 冷热分层）。

> Logstash 在 docker 网络内走 Kafka 的 `INTERNAL` listener(`kafka:29092`)，宿主机的 gateway/profiling-realtime 走 `localhost:9092`。

### Kibana 案件调查 (localhost:5601)

docker 起 `kibana` 后访问 http://localhost:5601 → Dev Tools 跑查询（脱敏后仍可按 `acct_hash` 关联）：

```
# 某账号(脱敏哈希)近一天所有交易, 按时间倒序 —— 案件调查
GET txn-*/_search
{ "query": { "term": { "acct_hash": "<HMAC值>" } },
  "sort": [{ "@timestamp": "desc" }] }

# 大额交易 Top 来源渠道聚合
GET txn-*/_search
{ "size":0, "query": { "range": { "amount": { "gte": 5000000 } } },
  "aggs": { "by_channel": { "terms": { "field": "channel" } } } }
```

Discover 里建 `txn-*` data view (时间字段 `@timestamp`) 即可可视化检索。

## 生产化能力

### 规则热发布 (不重启)

```bash
# 把一段 DRL 热发布生效; 编译失败返回 400 + 行号
curl -X POST localhost:8082/rules/reload -H "Content-Type: text/plain" --data-binary @your-rule.drl
```

### Sentinel 熔断降级 (§3.6)

决策接口 `risk-evaluate` 受流控(QPS=20) + 慢调用熔断(RT>50ms) 保护：超限/熔断时走
`blockHandler`/`fallback` 快速返回保守决策（`DEGRADED_RATELIMIT`/`FALLBACK_EXCEPTION`），不拖垮 SLA。

### Flink 实时特征 (profiling-realtime 平替)

```bash
# 与 profiling-realtime 二选一; 嵌入式本地 MiniCluster 运行
./mvnw -q -pl profiling-realtime-flink -am compile
./mvnw -q -pl profiling-realtime-flink exec:exec
```

Flink DataStream：KafkaSource(txn-events) → keyBy(账号) → 键控状态聚合(当日累计/笔数 + 5分钟滑窗) → 写 Redis。

### 模型重训调度 (DolphinScheduler)

```bash
docker compose up -d dolphinscheduler   # UI http://localhost:12345/dolphinscheduler/ui (admin/dolphinscheduler123)
```

在 UI 建项目 → 工作流 → **Shell 任务**执行 `scripts/retrain.sh`（worker 需挂载本仓库 + JDK21），
设 cron（如每周）定时重训随机森林并导出新 PMML。`scripts/retrain.sh` 即重训命令。

## 监控

`GET /actuator/prometheus`（P99 / 命中率埋点后续接入，参考 drools-demo Step 15）。

## 下一步 (按 PLAN 里程碑)

- M5：Flink CEP 时序规则 + 决策表 + KieScanner 规则热发布
- 超时熔断降级(Sentinel)、实时特征平替为 Flink、模型重训练调度(DolphinScheduler)、Kibana 案件调查界面
