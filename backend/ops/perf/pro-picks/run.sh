#!/usr/bin/env bash
#
# 오늘의 추천 픽 조회 성능: 정규화(자식 테이블 + 조인) vs 단일 테이블.
# 원본 DB(PERF_DB_URL)는 읽기만 하고, 같은 서버에 측정용 DB(T10_DB)를 새로 만들어 잰다. 끝나면 측정용 DB를 지운다(KEEP_DB=1이면 남긴다).
# 앱과 같은 네트워크 왕복을 재려면 perf EC2(앱 서버)에서 perf RDS로 실행한다. prod DB에는 돌리지 않는다.
#
#   export PERF_DB_URL=postgresql://<user>@<perf-rds-endpoint>:5432/<db>   # 비밀번호는 ~/.pgpass 또는 PGPASSWORD
#   ./backend/ops/perf/pro-picks/run.sh <배수> [측정 초]
#   예) ./backend/ops/perf/pro-picks/run.sh 23      # 약 한 시즌(2.5만 픽)
#       ./backend/ops/perf/pro-picks/run.sh 230     # 그 10배
#
# PERF_DB_URL이 없고 ~/app-config/.env가 있으면(perf EC2) 앱의 DB 설정으로 접속한다.
# 측정용 DB를 만들고 지우므로 CREATEDB 권한이 필요하다. pg_dump는 서버보다 버전이 같거나 높아야 한다.
#
set -euo pipefail
die() { echo "❌ $*" >&2; exit 1; }

COPIES=${1:?배수를 넣는다 (예: 23)}
DURATION=${2:-30}
T10_DB=${T10_DB:-dfgg_t10}
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
[[ "$SOURCE_DB" != "$T10_DB" ]] || die "측정용 DB 이름이 원본과 같다: $T10_DB"
T10_URL="${PERF_DB_URL%/*}/$T10_DB"
for tool in psql pg_dump pgbench; do command -v "$tool" > /dev/null || die "$tool이 없다"; done

export PGCONNECT_TIMEOUT=${PGCONNECT_TIMEOUT:-10}
psql_source() { psql "$PERF_DB_URL" -X -v ON_ERROR_STOP=1 -q "$@"; }
psql_t10() { psql "$T10_URL" -X -v ON_ERROR_STOP=1 -q "$@"; }

PICKS=$(psql_source -tA -c "SELECT count(*) FROM pro_picks")
[[ "$PICKS" -gt 0 ]] || die "원본 DB($SOURCE_DB)에 대회 픽이 없다"

OUT="$HERE/results/$(date -u +%Y%m%dT%H%M%SZ)-x${COPIES}"
mkdir -p "$OUT"
{
    echo "source: ${PERF_DB_URL#*@} (picks=$PICKS)"
    echo "client: $(hostname)"
    echo "server: $(psql_source -tA -c "SHOW server_version")"
    echo "copies: $COPIES  duration: ${DURATION}s"
} | tee "$OUT/meta.txt"

echo "== 1. 측정 DB 새로 만들기 ($T10_DB ← $SOURCE_DB)"
psql_source -c "DROP DATABASE IF EXISTS $T10_DB" -c "CREATE DATABASE $T10_DB"
cleanup() {
    [[ "${KEEP_DB:-}" == 1 ]] && return
    psql_source -c "DROP DATABASE IF EXISTS $T10_DB" || true
}
trap cleanup EXIT
pg_dump "$PERF_DB_URL" --no-owner --no-privileges \
    -t champions -t items -t runes -t summoner_spells \
    -t pro_tournaments -t pro_players -t pro_picks -t pro_pick_spells -t pro_pick_items -t pro_pick_runes \
  | sed '/^SET transaction_timeout = 0;$/d' \
  | psql_t10 > /dev/null

echo "== 2. 데이터 ${COPIES}배로 늘리기"
psql_t10 -v copies="$COPIES" < "$HERE/scale.sql"

echo "== 3. 단일 테이블 만들기"
psql_t10 < "$HERE/flatten.sql"
psql_t10 -c "VACUUM ANALYZE"
psql_t10 -c "SELECT (SELECT count(*) FROM pro_picks) AS picks,
                    (SELECT count(*) FROM pro_pick_spells) + (SELECT count(*) FROM pro_pick_items)
                      + (SELECT count(*) FROM pro_pick_runes) AS child_rows,
                    pg_size_pretty(pg_total_relation_size('pro_picks') + pg_total_relation_size('pro_pick_spells')
                      + pg_total_relation_size('pro_pick_items') + pg_total_relation_size('pro_pick_runes')) AS normalized_size,
                    pg_size_pretty(pg_total_relation_size('pro_picks_flat')) AS flat_size" | tee "$OUT/size.txt"

LOG_DIR=$(mktemp -d)
bench() { # $1 스크립트 이름, $2 클라이언트 수
    rm -f "$LOG_DIR"/pgbench_log.*
    (cd "$LOG_DIR" && pgbench -n -c "$2" -j "$2" -T "$DURATION" -l -f "$HERE/$1.sql" "$T10_URL" > summary.txt 2>&1) \
        || { cat "$LOG_DIR/summary.txt" >&2; die "pgbench 실패: $1"; }
    grep -E 'tps|latency average|number of transactions actually' "$LOG_DIR/summary.txt"
    cat "$LOG_DIR"/pgbench_log.* | awk '{print $3}' | sort -n | awk '{a[NR]=$1} END {printf "p50 %.2f ms  p95 %.2f ms  p99 %.2f ms  (n=%d)\n", a[int(NR*0.50)]/1000, a[int(NR*0.95)]/1000, a[int(NR*0.99)]/1000, NR}'
}

echo "== 4. 워밍업 (버림)"
bench normalized 1 > /dev/null; bench flat 1 > /dev/null; bench ping 1 > /dev/null

for clients in 1 8; do
    for round in 1 2 3; do
        for kind in normalized flat ping; do
            echo "== ${kind} c=${clients} round=${round}" | tee -a "$OUT/bench.txt"
            bench "$kind" "$clients" | tee -a "$OUT/bench.txt"
        done
    done
done

echo "== 5. 실행 계획"
TID=$(psql_t10 -tA -c "SELECT p.tournament_id FROM pro_picks p ORDER BY p.played_at DESC, p.tournament_id DESC LIMIT 1")
{
  for q in \
    "SELECT t.id FROM pro_picks p JOIN pro_tournaments t ON t.id = p.tournament_id ORDER BY p.played_at DESC, t.id DESC LIMIT 1" \
    "SELECT p.*, pl.*, t.* FROM pro_picks p JOIN pro_players pl ON pl.id = p.player_id JOIN pro_tournaments t ON t.id = p.tournament_id WHERE p.tournament_id = $TID AND p.position = 'MID' ORDER BY p.played_at DESC, p.id DESC" \
    "SELECT pick_id, slot, rune_id FROM pro_pick_runes WHERE pick_id = ANY(ARRAY(SELECT id FROM pro_picks WHERE tournament_id = $TID AND position = 'MID')) ORDER BY pick_id, slot" \
    "SELECT tournament_id FROM pro_picks_flat ORDER BY played_at DESC, tournament_id DESC LIMIT 1" \
    "SELECT * FROM pro_picks_flat WHERE tournament_id = $TID AND position = 'MID' ORDER BY played_at DESC, id DESC"; do
    echo "### $q"
    psql_t10 -c "EXPLAIN (ANALYZE, BUFFERS) $q"
  done
} > "$OUT/explain.txt"
rm -rf "$LOG_DIR"
echo "결과: $OUT"
