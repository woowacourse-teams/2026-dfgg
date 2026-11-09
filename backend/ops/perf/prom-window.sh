#!/usr/bin/env bash
#
# 측정 1회의 앱·서버 지표를 Prometheus에서 측정 구간만 잘라 results/<회차>/prometheus.txt에 남긴다.
# 모니터링 EC2에서 run.sh가 끝난 뒤 실행한다. perf nginx가 /actuator를 막아 앱 지표는 Prometheus로만 얻는다.
#
#   ./backend/ops/perf/prom-window.sh backend/ops/perf/results/<UTC>-v4-1 2m   # 두 번째 인자: 앞에서 버릴 워밍업(0s면 없음)
#
# 구간은 meta.txt의 started_at + 워밍업 ~ finished_at이다. 수집 간격이 30초라 구간 경계는 그만큼 거칠다.
# Hikari 대기(acquire)·점유(usage)는 히스토그램이 꺼져 있어 p95가 없고 평균·최대만 남긴다.

set -Eeuo pipefail
die() { echo "❌ $*" >&2; exit 1; }
say() { echo "▶ $*"; }

OUT="${1:-}"
WARMUP="${2:-}"
[[ -n "$OUT" && -n "$WARMUP" ]] || die "사용법: $0 <결과 폴더> <버릴 워밍업, 예: 2m|1m|0s>"
[[ -r "$OUT/meta.txt" ]] || die "meta.txt가 없다: $OUT"
PROM_URL="${PROM_URL:-http://127.0.0.1:9090}"
for cmd in curl jq; do
  command -v "$cmd" > /dev/null || die "$cmd가 없다 (sudo apt-get install -y $cmd)"
done
date -u -d @0 +%s > /dev/null 2>&1 || die "GNU date가 필요하다(모니터링 EC2에서 실행한다)"

meta() { sed -n "s/^$1=//p" "$OUT/meta.txt" | head -1; }

# k6 기간 문자열(2m, 1m30s, 0s)을 초로 바꾼다.
seconds_of() {
  local rest="$1" total=0 number unit
  [[ "$rest" =~ ^([0-9]+(ms|s|m|h))+$ ]] || die "기간 형식은 숫자+단위(ms|s|m|h)다: $1"
  while [[ "$rest" =~ ^([0-9]+)(ms|s|m|h)(.*)$ ]]; do
    number=${BASH_REMATCH[1]}
    unit=${BASH_REMATCH[2]}
    rest=${BASH_REMATCH[3]}
    case "$unit" in
      ms) ;;
      s) total=$((total + number)) ;;
      m) total=$((total + number * 60)) ;;
      h) total=$((total + number * 3600)) ;;
    esac
  done
  echo "$total"
}

STARTED_AT=$(meta started_at)
FINISHED_AT=$(meta finished_at)
[[ -n "$STARTED_AT" && -n "$FINISHED_AT" ]] || die "meta.txt에 started_at·finished_at이 없다"
ENV_LABEL=$(meta env)
ENV_LABEL=${ENV_LABEL:-perf}
ENDPOINT=$(meta endpoint)
ENDPOINT=${ENDPOINT:-v4}

# 워밍업 값은 meta에 따로 두지 않는다. k6에 넘긴 값과 다르면 구간이 어긋나니 알리기만 한다.
k6_warmup=$(meta k6_args | grep -o 'WARMUP=[^ ]*' | head -1 | cut -d= -f2 || true)
if [[ -n "$k6_warmup" && "$k6_warmup" != "$WARMUP" ]]; then
  echo "⚠️ k6에 넘긴 WARMUP=$k6_warmup 과 인자 $WARMUP 이 다르다" >&2
fi

START=$(( $(date -u -d "$STARTED_AT" +%s) + $(seconds_of "$WARMUP") ))
END=$(date -u -d "$FINISHED_AT" +%s)
RANGE=$((END - START))
(( RANGE > 0 )) || die "측정 구간이 없다: $STARTED_AT + $WARMUP ~ $FINISHED_AT"

# 마지막 수집분(30초 간격)이 올라올 때까지 기다린다.
wait_seconds=$((END + 60 - $(date -u +%s)))
if (( wait_seconds > 0 )); then
  say "마지막 수집분 대기 ${wait_seconds}초"
  sleep "$wait_seconds"
fi

APP="job=\"dfgg-app\",env=\"$ENV_LABEL\""
R="${RANGE}s"

# 값이 없거나 NaN이면 none. 질의 자체가 실패하면 멈춘다.
query() {
  local response
  response=$(curl -sfG "$PROM_URL/api/v1/query" --data-urlencode "query=$1" --data-urlencode "time=$END") \
    || die "Prometheus 질의 실패: $1"
  jq -r 'if (.data.result | length) == 0 then "none"
         else (.data.result[0].value[1] | if . == "NaN" then "none" else . end) end' <<< "$response"
}

quantile() {
  query "histogram_quantile($1, sum by (le) (increase($2[$R])))"
}

# 한 줄씩 대입한 뒤 쓴다. echo "k=$(query …)"로 쓰면 질의 실패(die)가 서브셸만 끝내고 빈 값이 남는다.
emit() {
  local value
  value=$("${@:2}")
  echo "$1=$value"
}

# 측정 구간 요청 수. stage total 증가분과 맞춰 보면 지표 구간이 측정 구간과 맞는지 안다(회차 유효성).
k6_measure_requests=none
if [[ -r "$OUT/k6-summary.json" ]]; then
  k6_measure_requests=$(jq -r '.metrics["http_req_failed{phase:measure}"] // empty | (.passes + .fails)' "$OUT/k6-summary.json")
  k6_measure_requests=${k6_measure_requests:-none}
fi

say "Prometheus 질의 → $OUT/prometheus.txt"
{
  echo "# window=$(date -u -d "@$START" +%Y-%m-%dT%H:%M:%SZ)~$FINISHED_AT (워밍업 $WARMUP 제외, ${RANGE}초) prom=$PROM_URL selector={$APP}"
  emit "app_cpu_max" query "max(max_over_time(process_cpu_usage{$APP}[$R]))"
  emit "heap_used_max_bytes" query "max_over_time(sum(jvm_memory_used_bytes{$APP,area=\"heap\"})[$R:15s])"
  emit "gc_pause_seconds_sum" query "sum(increase(jvm_gc_pause_seconds_sum{$APP}[$R]))"
  emit "hikari_active_max" query "max(max_over_time(hikaricp_connections_active{$APP}[$R]))"
  emit "hikari_pending_max" query "max(max_over_time(hikaricp_connections_pending{$APP}[$R]))"
  emit "hikari_acquire_avg_seconds" query "sum(increase(hikaricp_connections_acquire_seconds_sum{$APP}[$R])) / sum(increase(hikaricp_connections_acquire_seconds_count{$APP}[$R]))"
  emit "hikari_acquire_max_seconds" query "max(max_over_time(hikaricp_connections_acquire_seconds_max{$APP}[$R]))"
  # 커넥션 점유. 평균은 대여 1회당이다. open-in-view가 꺼져 있어 요청 1건이 여러 번 빌릴 수 있으므로
  # 요청당 점유는 usage_sum_increase ÷ stage_total_count_increase로 따로 구한다.
  emit "hikari_usage_avg_seconds" query "sum(increase(hikaricp_connections_usage_seconds_sum{$APP}[$R])) / sum(increase(hikaricp_connections_usage_seconds_count{$APP}[$R]))"
  emit "hikari_usage_max_seconds" query "max(max_over_time(hikaricp_connections_usage_seconds_max{$APP}[$R]))"
  emit "hikari_usage_sum_increase" query "sum(increase(hikaricp_connections_usage_seconds_sum{$APP}[$R]))"
  emit "hikari_usage_count_increase" query "sum(increase(hikaricp_connections_usage_seconds_count{$APP}[$R]))"
  emit "http_p95_seconds" quantile 0.95 "http_server_requests_seconds_bucket{$APP,uri=\"/api/recommendations/$ENDPOINT\"}"
  # v4 단계별 시간. v3 회차나 계측 전 JAR에서는 none이다.
  for stage in prepare core situational component total; do
    emit "stage_p50_$stage" quantile 0.5 "recommendation_v4_stage_seconds_bucket{$APP,stage=\"$stage\"}"
    emit "stage_p95_$stage" quantile 0.95 "recommendation_v4_stage_seconds_bucket{$APP,stage=\"$stage\"}"
  done
  emit "stage_total_count_increase" query "sum(increase(recommendation_v4_stage_seconds_count{$APP,stage=\"total\"}[$R]))"
  emit "empty_core_increase" query "sum(increase(recommendation_v4_empty_core_total{$APP}[$R]))"
  echo "k6_measure_requests=$k6_measure_requests"
  emit "perf_node_cpu_avg" query "1 - avg(rate(node_cpu_seconds_total{job=\"dfgg-node\",env=\"$ENV_LABEL\",mode=\"idle\"}[$R]))"
  # 부하 발생기 포화 확인용. k6 프로세스만이 아니라 같은 EC2의 Prometheus·Grafana까지 합친 CPU다.
  emit "monitoring_node_cpu_max" query "max_over_time((1 - avg(rate(node_cpu_seconds_total{env=\"monitoring\",mode=\"idle\"}[1m])))[$R:30s])"
} > "$OUT/prometheus.txt.tmp"
mv "$OUT/prometheus.txt.tmp" "$OUT/prometheus.txt"

cat "$OUT/prometheus.txt"
