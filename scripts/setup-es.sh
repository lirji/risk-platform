#!/usr/bin/env bash
# 应用交易、决策索引模板与决策保留策略。ES 起来后、写入前执行一次。
# 用法: ./scripts/setup-es.sh [ES_URL] [KIBANA_URL]
# 默认: http://localhost:9200 http://localhost:5601
set -euo pipefail

ES_URL="${1:-http://localhost:9200}"
KIBANA_URL="${2:-http://localhost:5601}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "等待 ES 就绪 ($ES_URL) ..."
for i in $(seq 1 30); do
  if curl -fs "$ES_URL" >/dev/null 2>&1; then break; fi
  sleep 2
done

echo "应用 index template: txn-template"
curl -fs -X PUT "$ES_URL/_index_template/txn-template" \
  -H 'Content-Type: application/json' \
  --data-binary "@$ROOT_DIR/es-init/txn-index-template.json"
echo

echo "应用 ILM policy: risk-decision-retention"
curl -fs -X PUT "$ES_URL/_ilm/policy/risk-decision-retention" \
  -H 'Content-Type: application/json' \
  --data-binary "@$ROOT_DIR/es-init/risk-decision-ilm.json"
echo

echo "应用 index template: risk-decision-template"
curl -fs -X PUT "$ES_URL/_index_template/risk-decision-template" \
  -H 'Content-Type: application/json' \
  --data-binary "@$ROOT_DIR/es-init/risk-decision-template.json"
echo

if curl -fs "$KIBANA_URL/api/status" >/dev/null 2>&1; then
  echo "导入 Kibana saved objects"
  curl -fs -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
    -H 'kbn-xsrf: true' \
    --form "file=@$ROOT_DIR/observability/kibana/risk-platform.ndjson"
  echo
else
  echo "Kibana 尚未就绪，跳过 saved objects 导入；稍后可用第二个参数重试。"
fi

echo "完成。后续 txn-* 与 risk-decisions-* 索引将按受控 mapping 创建。"
