#!/bin/sh
set -eu

nacos_addr="${NACOS_ADDR:-http://nacos:8848}"
username="${NACOS_USERNAME:-nacos}"
password="${NACOS_PASSWORD:?NACOS_PASSWORD must be set}"

attempt=0
until curl -fsS "$nacos_addr/nacos/v1/console/health/readiness" >/dev/null; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "Nacos did not become ready" >&2
    exit 1
  fi
  sleep 2
done

for data_id in risk-admin.yml fraud-gateway.yml; do
  curl -fsS -X POST "$nacos_addr/nacos/v1/cs/configs" \
    --data-urlencode "dataId=$data_id" \
    --data-urlencode "group=RISK_PLATFORM" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@/config/$data_id" \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$password"
done

echo "Nacos risk configuration published"
