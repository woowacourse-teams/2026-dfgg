#!/usr/bin/env bash
#
# 부하 측정 1회를 실행하고 결과를 results/<UTC>-<tag>/에 남긴다.
# 모니터링 EC2에서 실행한다.
#
#   export BASE_URL=http://<perf-ec2-private-ip>:8080
#   export PERF_DB_URL=postgresql://<user>@<perf-rds-endpoint>:5432/<db>   # 비밀번호는 ~/.pgpass
#   export PERF_DB_INSTANCE=dfgg-db-perf                                   # CloudWatch 지표용
#   export APP_COMMIT=... PERF_DATA=serving-YYMMDD.dump                    # meta에 남길 앱·데이터 식별자
#   ./backend/ops/perf/run.sh before-1 -e SCENARIO=target -e TARGET_RPS=10

set -Eeuo pipefail
die() { echo "❌ $*" >&2; exit 1; }
say() { echo "▶ $*"; }

TAG="${1:-}"
[[ -n "$TAG" && "$TAG" != -* ]] || die "사용법: $0 <tag> [k6 run 옵션...]"
shift

: "${BASE_URL:?BASE_URL이 필요하다 (perf EC2의 nginx, 예: http://10.0.0.x:8080)}"
: "${PERF_DB_URL:?PERF_DB_URL이 필요하다 (psql 접속 문자열)}"
PERF_DB_INSTANCE="${PERF_DB_INSTANCE:-}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"

PERF_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REQUESTS="${REQUESTS:-$PERF_DIR/k6/data/requests.json}"
[[ -r "$REQUESTS" ]] || die "요청 파일이 없다: $REQUESTS (k6/make-requests.sql로 만든다)"
command -v k6 > /dev/null || die "k6가 없다"
command -v psql > /dev/null || die "psql이 없다"

STARTED_EPOCH=$(date -u +%s)
STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
OUT="$PERF_DIR/results/$(date -u +%Y%m%dT%H%M%SZ)-$TAG"
mkdir -p "$OUT"

# 확장이 없으면 쿼리 통계 없이 진행한다. 병목 판정 근거가 하나 줄어드니 경고는 남긴다.
HAS_PGSS=false
if psql "$PERF_DB_URL" -XAtqc "SELECT pg_stat_statements_reset()" > /dev/null 2>&1; then
  HAS_PGSS=true
else
  echo "⚠️ pg_stat_statements를 쓸 수 없다. 쿼리 통계 없이 진행한다" >&2
fi

say "k6 실행 → $OUT"
set +e
k6 run -e BASE_URL="$BASE_URL" -e REQUESTS="$REQUESTS" "$@" \
  --summary-export="$OUT/k6-summary.json" "$PERF_DIR/k6/v3-load.js" 2>&1 | tee "$OUT/k6.log"
K6_EXIT=${PIPESTATUS[0]}
set -e
FINISHED_EPOCH=$(date -u +%s)
FINISHED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

# k6 종료 코드 99는 threshold 미달이다. 측정은 끝난 것이므로 수집은 계속한다.
say "수집"
if [[ "$HAS_PGSS" == true ]]; then
  psql "$PERF_DB_URL" -Xq --csv -c "
      SELECT calls,
             round(total_exec_time::numeric, 1) AS total_ms,
             round(mean_exec_time::numeric, 2) AS mean_ms,
             rows,
             shared_blks_hit,
             shared_blks_read,
             left(regexp_replace(query, '\s+', ' ', 'g'), 300) AS query
      FROM pg_stat_statements
      WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
      ORDER BY total_exec_time DESC
      LIMIT 20" > "$OUT/pg_stat_statements.csv"
fi

# 공유 계정이라 DBInstanceIdentifier를 반드시 고정한다. 빼면 남의 팀 RDS가 평균에 섞인다.
# RDS 기본 지표는 1분 단위라 앞뒤로 1분씩 넓혀 측정 구간을 덮는다.
iso_at() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -r "$1" +%Y-%m-%dT%H:%M:%SZ; }
if [[ -n "$PERF_DB_INSTANCE" ]] && ! command -v aws > /dev/null; then
  echo "⚠️ aws CLI가 없어 CloudWatch 지표를 건너뛴다" >&2
elif [[ -n "$PERF_DB_INSTANCE" ]]; then
  CW_START=$(iso_at $((STARTED_EPOCH - 60)))
  CW_END=$(iso_at $((FINISHED_EPOCH + 60)))
  # RDS 지표는 몇 분 늦게 올라온다. 측정 직후에 읽으면 측정 구간이 비어 직전 구간 값이 남는다
  # (2026-10-12 smoke: 측정 분의 CPU 54.7%가 아니라 직전 분의 3.4%를 기록했다).
  # 측정 마지막 분의 CPU 값이 올라올 때까지 기다린다.
  LAST_MINUTE=$((FINISHED_EPOCH / 60 * 60))
  waited=0
  until [[ "$(aws cloudwatch get-metric-statistics --region "$AWS_REGION" --namespace AWS/RDS --metric-name CPUUtilization \
           --dimensions Name=DBInstanceIdentifier,Value="$PERF_DB_INSTANCE" \
           --start-time "$(iso_at "$LAST_MINUTE")" --end-time "$(iso_at $((LAST_MINUTE + 60)))" --period 60 \
           --statistics Maximum --query 'length(Datapoints)' --output text 2>/dev/null)" == 1 ]]; do
    if (( waited >= ${CW_WAIT_MAX_SECONDS:-420} )); then
      echo "⚠️ ${waited}초 기다려도 측정 마지막 분의 지표가 없다. 지금 있는 값으로 남긴다" >&2
      break
    fi
    say "CloudWatch 지표 대기 (${waited}초 — RDS 지표는 몇 분 늦게 올라온다)"
    sleep 30
    waited=$((waited + 30))
  done
  {
    echo "instance=$PERF_DB_INSTANCE window=$STARTED_AT~$FINISHED_AT (앞뒤 1분 포함, 1분 단위 — points가 구간 분 수보다 적으면 빠진 분이 있다)"
    for metric in BurstBalance:Minimum CPUUtilization:Maximum ReadIOPS:Maximum ReadLatency:Maximum \
                  DiskQueueDepth:Maximum FreeableMemory:Minimum DatabaseConnections:Maximum; do
      name=${metric%%:*}
      stat=${metric##*:}
      values=$(aws cloudwatch get-metric-statistics --region "$AWS_REGION" --namespace AWS/RDS --metric-name "$name" \
        --dimensions Name=DBInstanceIdentifier,Value="$PERF_DB_INSTANCE" \
        --start-time "$CW_START" --end-time "$CW_END" --period 60 --statistics "$stat" \
        --query "Datapoints[].$stat" --output text) || values=""
      if [[ "$stat" == Minimum ]]; then
        picked=$(tr '\t' '\n' <<< "$values" | awk 'NF' | sort -g | head -1)
      else
        picked=$(tr '\t' '\n' <<< "$values" | awk 'NF' | sort -g | tail -1)
      fi
      points=$(tr '\t' '\n' <<< "$values" | awk 'NF' | wc -l | tr -d ' ')
      echo "$name $stat=${picked:-None} points=$points"
    done
  } > "$OUT/cloudwatch.txt"
  if grep -q "=None" "$OUT/cloudwatch.txt"; then
    echo "⚠️ CloudWatch 값이 빈 지표가 있다 (식별자·IAM 권한 확인)" >&2
  fi
fi

{
  echo "tag=$TAG"
  echo "started_at=$STARTED_AT"
  echo "finished_at=$FINISHED_AT"
  echo "k6_exit=$K6_EXIT"
  echo "k6_args=$*"
  echo "k6_version=$(k6 version 2>/dev/null | head -1)"
  echo "base_url=$BASE_URL"
  echo "db_instance=${PERF_DB_INSTANCE:-unset}"
  echo "requests_sha256=$( (sha256sum "$REQUESTS" 2>/dev/null || shasum -a 256 "$REQUESTS") | cut -d' ' -f1)"
  echo "repo_commit=$(git -C "$PERF_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  echo "pg_stat_statements=$HAS_PGSS"
  # 앱이 어느 커밋으로 떠 있는지는 여기서 알 수 없다. 배포한 커밋을 APP_COMMIT으로 넘긴다.
  echo "app_commit=${APP_COMMIT:-unset}"
  # perf DB에 실은 고정 덤프. 전후 비교는 같은 덤프여야 한다(README "서빙 데이터 적재").
  echo "perf_data=${PERF_DATA:-unset}"
} > "$OUT/meta.txt"

say "완료 (k6 exit=$K6_EXIT)"
ls -1 "$OUT"
if [[ -s "$OUT/cloudwatch.txt" ]]; then
  cat "$OUT/cloudwatch.txt"
fi
exit "$K6_EXIT"
