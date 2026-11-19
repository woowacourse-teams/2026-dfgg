#!/usr/bin/env bash
# main 통계를 다시 계산한다. scheduling 서버에서 활성 슬롯의 POST /admin/mining/all을 한 번 부른다.
# 집계 단계와 순서는 앱(StatsAggregationStep 구현)이 정한다. 이 스크립트는 호출과 판정만 한다.
#
#   사용법: aggregate-main.sh [--dry-run]
#   --dry-run  활성 슬롯과 readiness만 확인하고 집계는 부르지 않는다
#
#   UPSTREAM_CONF      Nginx upstream 파일 (기본값 /etc/nginx/conf.d/dfgg-upstream.conf)
#   AGGREGATE_TIMEOUT  집계 응답을 기다릴 최대 초 (기본값 3600)

set -Eeuo pipefail

upstream=${UPSTREAM_CONF:-/etc/nginx/conf.d/dfgg-upstream.conf}
timeout=${AGGREGATE_TIMEOUT:-3600}
dry_run=false

usage() { echo "usage: $0 [--dry-run]" >&2; exit 2; }
fail() { echo "error: $*" >&2; exit 1; }

while (($#)); do
  case "$1" in
    --dry-run) dry_run=true; shift ;;
    *) usage ;;
  esac
done
[[ "$timeout" =~ ^[0-9]+$ ]] || fail "invalid AGGREGATE_TIMEOUT: $timeout"

# 8080(Nginx)은 60초 타임아웃에 걸리므로 활성 슬롯 포트로 직접 부른다.
port=$(grep -oE '127\.0\.0\.1:[0-9]+' "$upstream" | cut -d: -f2) || fail "cannot read $upstream"
[[ "$port" =~ ^(8081|8082)$ ]] || fail "unexpected upstream port: ${port:-none}"
base="http://127.0.0.1:$port"

readiness=$(curl --noproxy '*' -sS --max-time 10 "$base/actuator/health/readiness") || fail "active slot not reachable: $base"
jq -e '.status == "UP"' <<< "$readiness" > /dev/null || fail "active slot is not ready: $readiness"

if $dry_run; then
  echo "would POST $base/admin/mining/all (timeout ${timeout}s)"
  exit 0
fi

body=$(mktemp)
trap 'rm -f "$body"' EXIT
started=$(date +%s)
echo "aggregate: POST $base/admin/mining/all"
code=$(curl --noproxy '*' -sS --max-time "$timeout" -X POST -o "$body" -w '%{http_code}' \
  "$base/admin/mining/all") || fail "request failed after $(( $(date +%s) - started ))s"
elapsed=$(( $(date +%s) - started ))

if [[ "$code" != 200 ]]; then
  failed=$(jq -r '.failedStep // "unknown"' "$body" 2> /dev/null || echo unknown)
  completed=$(jq -r '(.completedSteps // []) | join(",")' "$body" 2> /dev/null || true)
  fail "aggregation failed: http=$code step=$failed completed=[${completed}] elapsed=${elapsed}s"
fi

jq -r '.steps[] | "step \(.name): \(.durationMillis)ms"' "$body"
echo "aggregate done: $(jq -r '.steps | length' "$body") steps, ${elapsed}s"
