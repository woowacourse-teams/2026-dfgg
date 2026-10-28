#!/usr/bin/env bash
#
# 카운터 API(POST /api/counters)의 DB 작업을 세 방식으로 비교한다.
#   before  : 이전 코드 — 상대마다 카운터 쿼리 1번 (상대 5명이면 쿼리 9개)
#   batched : 지금 코드 — 상대를 모아 IN 한 번 (쿼리 5개)
#   denorm  : 비정규화 — (상대, 내 라인)마다 이름·라인 분포·카운터를 한 행에 미리 모은 counter_view (쿼리 2개)
# 원본(PERF_DB_URL)은 읽기만 하고, 같은 서버에 측정용 DB(MEASURE_DB)를 새로 만들어 잰다. 끝나면 지운다(KEEP_DB=1이면 남긴다).
# 앱과 같은 네트워크 왕복을 재려면 perf EC2(앱 서버)에서 perf RDS로 실행한다. prod DB에는 돌리지 않는다.
#
#   export PERF_DB_URL=postgresql://<user>@<perf-rds-endpoint>:5432/<db>   # 비밀번호는 ~/.pgpass 또는 PGPASSWORD
#   ./backend/ops/perf/counters/run.sh [측정 초]
#
# 선택:
#   SOURCE_DUMP=<파일>  원본 DB 대신 이 SQL 덤프(champions·champion_counters·champion_position_stats)로 측정 DB를 채운다
#   MODE=prepared       pgbench 쿼리 방식(-M). 기본 simple
#   POSITION=MID        내 라인
#
# PERF_DB_URL이 없고 ~/app-config/.env가 있으면(perf EC2) 앱의 DB 설정으로 접속한다.
#
set -euo pipefail
die() { echo "❌ $*" >&2; exit 1; }

DURATION=${1:-15}
MEASURE_DB=${MEASURE_DB:-dfgg_counters}
MODE=${MODE:-simple}
POSITION=${POSITION:-MID}
HERE=$(cd "$(dirname "$0")" && pwd)

app_env() { grep -m1 "^$1=" ~/app-config/.env | cut -d= -f2-; }
if [[ -z "${PERF_DB_URL:-}" && -r ~/app-config/.env ]]; then
    JDBC=$(app_env DB_URL)
    PERF_DB_URL="postgresql://$(app_env DB_USERNAME)@${JDBC#jdbc:postgresql://}"
    PERF_DB_URL=${PERF_DB_URL%%\?*}
    PGPASSWORD=$(app_env DB_PASSWORD)
    export PGPASSWORD
fi
: "${PERF_DB_URL:?PERF_DB_URL이 필요하다 (예: postgresql://user@host:5432/dfgg)}"
[[ "${PERF_DB_URL#*@}" == dfgg-db-prod* ]] && die "prod DB에는 돌리지 않는다: ${PERF_DB_URL#*@}"
SOURCE_DB=${PERF_DB_URL##*/}
[[ "$SOURCE_DB" != "$MEASURE_DB" ]] || die "측정용 DB 이름이 원본과 같다: $MEASURE_DB"
MEASURE_URL="${PERF_DB_URL%/*}/$MEASURE_DB"
for tool in psql pg_dump pgbench; do command -v "$tool" > /dev/null || die "$tool이 없다"; done
[[ -z "${SOURCE_DUMP:-}" || -r "$SOURCE_DUMP" ]] || die "덤프 파일을 읽을 수 없다: $SOURCE_DUMP"

export PGCONNECT_TIMEOUT=${PGCONNECT_TIMEOUT:-10}
psql_source() { psql "$PERF_DB_URL" -X -v ON_ERROR_STOP=1 -q "$@"; }
psql_m() { psql "$MEASURE_URL" -X -v ON_ERROR_STOP=1 -tA "$@"; }

OUT="$HERE/results/$(date -u +%Y%m%dT%H%M%SZ)-${MODE}"
mkdir -p "$OUT"
{
    echo "source: ${PERF_DB_URL#*@}${SOURCE_DUMP:+ (dump: $(basename "$SOURCE_DUMP"))}"
    echo "client: $(hostname)"
    echo "server: $(psql_source -tA -c "SHOW server_version")"
    echo "mode: $MODE  duration: ${DURATION}s"
} | tee "$OUT/meta.txt"

echo "== 측정 DB 새로 만들기 ($MEASURE_DB)"
psql_source -c "DROP DATABASE IF EXISTS $MEASURE_DB" -c "CREATE DATABASE $MEASURE_DB"
cleanup() {
    [[ "${KEEP_DB:-}" == 1 ]] && return
    psql_source -c "DROP DATABASE IF EXISTS $MEASURE_DB" || true
}
trap cleanup EXIT
if [[ -n "${SOURCE_DUMP:-}" ]]; then
    sed '/^SET transaction_timeout = 0;$/d' "$SOURCE_DUMP" | psql_m -q > /dev/null
else
    pg_dump "$PERF_DB_URL" --no-owner --no-privileges -t champions -t champion_counters -t champion_position_stats \
      | sed '/^SET transaction_timeout = 0;$/d' | psql_m -q > /dev/null
fi
[[ "$(psql_m -c "SELECT count(*) FROM champion_counters")" -gt 0 ]] || die "카운터 데이터가 없다"

# 내 라인 카운터가 들어 있는 상대 5명을 고른다(요청의 최대 상대 수).
ENEMIES=$(psql_m -c "SELECT string_agg(e::text, ',') FROM (SELECT DISTINCT enemy_champion_id e FROM champion_counters
                     WHERE position = '$POSITION' ORDER BY 1 LIMIT 5) x")
FIRST=${ENEMIES%%,*}
# 앱처럼 집계된 패치 중 버전 순서로 가장 높은 패치를 기준으로 쓴다.
PATCH=$(psql_m -c "SELECT patch FROM (SELECT DISTINCT patch FROM champion_position_stats) p
                   ORDER BY string_to_array(patch, '.')::int[] DESC LIMIT 1")
psql_m -v patch="$PATCH" -q < "$HERE/denormalize.sql"
psql_m -c "VACUUM ANALYZE" > /dev/null
psql "$MEASURE_URL" -X -c "SELECT count(*) AS counters, (SELECT count(*) FROM champion_position_stats) AS position_stats,
                    (SELECT count(*) FROM champions) AS champions,
                    (SELECT count(*) FROM counter_view) AS counter_view_rows,
                    pg_size_pretty(pg_total_relation_size('champion_counters') + pg_total_relation_size('champion_position_stats')) AS normalized_size,
                    pg_size_pretty(pg_total_relation_size('counter_view')) AS denorm_size
             FROM champion_counters" | tee "$OUT/size.txt"
echo "position=$POSITION enemies=$ENEMIES patch=$PATCH" | tee -a "$OUT/size.txt"

# $1 상대 목록(쉼표), $2 방식(before|batched|denorm) → pgbench 스크립트
script() {
    local enemies=$1 kind=$2
    if [ "$kind" = denorm ]; then
        cat <<SQL
SELECT champion_id FROM champions WHERE champion_id = ANY('{${enemies}}'::bigint[]);
SELECT * FROM counter_view WHERE enemy_champion_id = ANY('{${enemies}}'::int[]) AND position = '$POSITION';
SQL
        return
    fi
    cat <<SQL
SELECT champion_id FROM champions WHERE champion_id = ANY('{${enemies}}'::bigint[]);
SELECT DISTINCT patch FROM champion_position_stats;
SELECT * FROM champion_position_stats WHERE patch = '${PATCH}' AND champion_id = ANY('{${enemies}}'::int[]);
SQL
    if [ "$kind" = before ]; then
        for e in ${enemies//,/ }; do
            echo "SELECT * FROM champion_counters WHERE enemy_champion_id = $e AND position = '$POSITION' ORDER BY rank;"
        done
    else
        echo "SELECT * FROM champion_counters WHERE enemy_champion_id = ANY('{${enemies}}'::int[]) AND position = '$POSITION' ORDER BY enemy_champion_id, rank;"
    fi
    cat <<SQL
SELECT * FROM champions WHERE champion_id = ANY(ARRAY(
    SELECT counter_champion_id::bigint FROM champion_counters
    WHERE enemy_champion_id = ANY('{${enemies}}'::int[]) AND position = '$POSITION'
    UNION SELECT unnest('{${enemies}}'::bigint[])));
SQL
}

for n in 1 5; do
    list=$ENEMIES; [ "$n" = 1 ] && list=$FIRST
    for kind in before batched denorm; do
        script "$list" "$kind" > "$OUT/$kind-$n.sql"
    done
done
# 쿼리 하나의 왕복 시간(네트워크 + 최소 실행).
echo "SELECT 1;" > "$OUT/ping.sql"

LOG_DIR=$(mktemp -d)
bench() { # $1 방식, $2 상대 수, $3 클라이언트 수
    local file="$OUT/$1-$2.sql"
    [[ "$1" == ping ]] && file="$OUT/ping.sql"
    rm -f "$LOG_DIR"/pgbench_log.*
    (cd "$LOG_DIR" && pgbench -n -M "$MODE" -c "$3" -j "$3" -T "$DURATION" -l -f "$file" "$MEASURE_URL" > summary.txt 2>&1) \
        || { cat "$LOG_DIR/summary.txt" >&2; die "pgbench 실패: $1"; }
    grep -E 'tps' "$LOG_DIR/summary.txt"
    cat "$LOG_DIR"/pgbench_log.* | awk '{print $3}' | sort -n | awk '{a[NR]=$1} END {printf "p50 %.2f ms  p95 %.2f ms  p99 %.2f ms  (n=%d)\n", a[int(NR*0.50)]/1000, a[int(NR*0.95)]/1000, a[int(NR*0.99)]/1000, NR}'
}

echo "== 워밍업 (버림)"
bench before 5 1 > /dev/null; bench batched 5 1 > /dev/null; bench denorm 5 1 > /dev/null; bench ping 1 1 > /dev/null

for n in 1 5; do
    for clients in 1 8; do
        for round in 1 2 3; do
            for kind in before batched denorm; do
                echo "== ${kind} enemies=${n} c=${clients} round=${round}" | tee -a "$OUT/bench.txt"
                bench "$kind" "$n" "$clients" | tee -a "$OUT/bench.txt"
            done
        done
    done
done
for round in 1 2 3; do
    echo "== ping enemies=0 c=1 round=${round}" | tee -a "$OUT/bench.txt"
    bench ping 1 1 | tee -a "$OUT/bench.txt"
done

{
  echo "### 상대 한 명의 카운터 (이전 방식, 상대마다 반복)"
  psql "$MEASURE_URL" -X -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM champion_counters WHERE enemy_champion_id = $FIRST AND position = '$POSITION' ORDER BY rank"
  echo "### 비정규화: 상대 다섯 명의 행을 한 번에"
  psql "$MEASURE_URL" -X -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM counter_view WHERE enemy_champion_id = ANY('{$ENEMIES}'::int[]) AND position = '$POSITION'"
  echo "### 상대 다섯 명의 카운터를 한 번에"
  psql "$MEASURE_URL" -X -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM champion_counters WHERE enemy_champion_id = ANY('{$ENEMIES}'::int[]) AND position = '$POSITION' ORDER BY enemy_champion_id, rank"
} > "$OUT/explain.txt"
rm -rf "$LOG_DIR"
echo "결과: $OUT"
