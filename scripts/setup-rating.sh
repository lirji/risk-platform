#!/usr/bin/env bash
# 初始化离线评级引擎 (架构 A): 灌 MySQL 配置中心 schema+种子 + 造 ES 标签数据源。
# 前置: docker compose up -d mysql elasticsearch
# 用法: ./scripts/setup-rating.sh
set -euo pipefail
cd "$(dirname "$0")/.."

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-13306}"
ES_URL="${ES_URL:-http://localhost:9200}"

echo "[1/3] 等 MySQL 就绪 ($MYSQL_HOST:$MYSQL_PORT)..."
for i in $(seq 1 30); do
  if docker exec risk-mysql mysqladmin ping -uroot -proot123 --silent >/dev/null 2>&1; then break; fi
  sleep 2
done

echo "[2/3] 灌入配置中心 schema + 种子..."
docker exec -i risk-mysql mysql -uroot -proot123 risk_platform < sql/rating-config-schema.sql
echo "  规则数: $(docker exec risk-mysql mysql -uroot -proot123 -N -e 'SELECT COUNT(*) FROM risk_platform.t_score_rule')"

echo "[3/3] 造 ES 标签数据源 cust-tags (3 个客户)..."
curl -s -X PUT "$ES_URL/cust-tags" -H 'Content-Type: application/json' -d '{
  "mappings": {"properties": {
    "cust_id":{"type":"keyword"}, "amount_90d":{"type":"double"},
    "txn_cnt_90d":{"type":"long"}, "counterparty":{"type":"long"},
    "avg_balance":{"type":"double"}, "night_ratio":{"type":"double"}
  }}}' >/dev/null || true
# 高风险客户(应评D) / 中风险(B~C) / 低风险(A)
curl -s -X POST "$ES_URL/cust-tags/_bulk" -H 'Content-Type: application/json' --data-binary '
{"index":{"_id":"C001"}}
{"cust_id":"C001","amount_90d":5000000,"txn_cnt_90d":350,"counterparty":80,"avg_balance":500,"night_ratio":0.7}
{"index":{"_id":"C002"}}
{"cust_id":"C002","amount_90d":1200000,"txn_cnt_90d":60,"counterparty":10,"avg_balance":8000,"night_ratio":0.2}
{"index":{"_id":"C003"}}
{"cust_id":"C003","amount_90d":30000,"txn_cnt_90d":5,"counterparty":2,"avg_balance":50000,"night_ratio":0.05}
' >/dev/null
curl -s -X POST "$ES_URL/cust-tags/_refresh" >/dev/null
echo "  cust-tags 文档数: $(curl -s "$ES_URL/cust-tags/_count" | grep -oE '"count":[0-9]+')"
echo "完成。运行引擎: ./mvnw -q -pl rating-engine -am compile && ./mvnw -q -pl rating-engine exec:exec"
