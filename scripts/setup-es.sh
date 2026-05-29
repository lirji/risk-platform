#!/usr/bin/env bash
# 应用交易检索索引模板到 ES (PLAN §2.5)。ES 起来后、写入前执行一次。
# 用法: ./scripts/setup-es.sh [ES_URL]   默认 http://localhost:9200
set -euo pipefail

ES_URL="${1:-http://localhost:9200}"
TEMPLATE_FILE="$(dirname "$0")/../es-init/txn-index-template.json"

echo "等待 ES 就绪 ($ES_URL) ..."
for i in $(seq 1 30); do
  if curl -fs "$ES_URL" >/dev/null 2>&1; then break; fi
  sleep 2
done

echo "应用 index template: txn-template"
curl -fs -X PUT "$ES_URL/_index_template/txn-template" \
  -H 'Content-Type: application/json' \
  --data-binary "@$TEMPLATE_FILE"
echo
echo "完成。后续 txn-* 索引将按此 mapping 创建。"
