#!/usr/bin/env bash
# patch-status.sh를 로컬 Postgres로 시험한다. 접속은 PGHOST·PGUSER·PGPASSWORD·PGDATABASE로 받는다.
# 시험용 데이터베이스(patch_status_test)를 만들고 끝나면 지운다.
#
#   사용법 (Docker):
#   docker run --rm -e POSTGRES_PASSWORD=test -v "$PWD/backend/ops/pipeline:/w:ro" postgres:16 \
#     bash -c 'docker-entrypoint.sh postgres > /dev/null 2>&1 & until pg_isready -q -h localhost; do sleep 1; done;
#              PGHOST=localhost PGUSER=postgres PGPASSWORD=test /w/test/patch-status-test.sh'

set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
pipeline=$(dirname "$here")
work=$(mktemp -d)
db=patch_status_test
trap 'rm -rf "$work"; psql -X -q -d postgres -c "DROP DATABASE IF EXISTS $db" > /dev/null' EXIT

psql -X -q -d postgres -c "DROP DATABASE IF EXISTS $db" -c "CREATE DATABASE $db"
export PGDATABASE=$db

# 실제 스키마 중 스크립트가 읽는 두 테이블만 만든다.
psql -X -q -v ON_ERROR_STOP=1 -o /dev/null <<'SQL'
CREATE TABLE normalized_match_participants (
    id bigserial PRIMARY KEY,
    champion_id integer NOT NULL,
    core_item_purchase_order text NOT NULL,
    core_item_purchase_order_complete boolean NOT NULL,
    final_core_item_ids text NOT NULL DEFAULT '',
    match_id varchar(32) NOT NULL,
    participant_id integer NOT NULL DEFAULT 1,
    patch varchar(16) NOT NULL,
    position varchar(32),
    puuid varchar(128) NOT NULL DEFAULT 'p',
    queue_id integer NOT NULL DEFAULT 420,
    team_id integer NOT NULL DEFAULT 100,
    win boolean NOT NULL DEFAULT true,
    tier varchar(32)
);
CREATE TABLE stats_aggregation_completions (
    id bigserial PRIMARY KEY,
    aggregation_revision varchar(16) NOT NULL DEFAULT 'r1',
    completed_at timestamptz NOT NULL,
    match_id varchar(32) NOT NULL,
    puuid varchar(128) NOT NULL DEFAULT 'p',
    queue_type varchar(32) NOT NULL DEFAULT 'RANKED_SOLO_5x5',
    tier varchar(32) NOT NULL DEFAULT 'PLATINUM'
);

-- 완료 빌드 n건을 넣는다. 경기 번호는 패치마다 따로 센다.
CREATE FUNCTION builds(p_patch text, p_champion int, p_position text, n int, complete boolean DEFAULT true,
                       p_order text DEFAULT '3031,3006') RETURNS void LANGUAGE sql AS $$
    INSERT INTO normalized_match_participants (champion_id, core_item_purchase_order,
        core_item_purchase_order_complete, match_id, patch, position)
    SELECT p_champion, p_order, complete, p_patch || '-' || p_champion || '-' || coalesce(p_position, 'x') || '-' || g,
           p_patch, p_position
    FROM generate_series(1, n) g
$$;

-- 16.9 < 16.20 < 16.21. 문자열로 비교하면 16.9가 가장 크다.
SELECT builds('16.9', 1, 'TOP', 500);
SELECT builds('16.20', 4, 'JUNGLE', 400);
-- 현재 패치 16.21
SELECT builds('16.21', 1, 'MIDDLE', 150);   -- MIDDLE과 MID를 합쳐 MID 300건
SELECT builds('16.21', 1, 'MID', 150);
SELECT builds('16.21', 1, 'TOP', 20);
SELECT builds('16.21', 2, 'TOP', 120);
SELECT builds('16.21', 3, 'BOTTOM', 50);
SELECT builds('16.21', 3, 'BOTTOM', 100, false);   -- 미완료 빌드는 세지 않는다
SELECT builds('16.21', 3, '', 100);                -- 포지션이 없는 경기(칼바람 등)는 세지 않는다
SELECT builds('16.21', 3, 'UTILITY', 100, true, '');  -- 구매 순서가 빈 빌드는 세지 않는다

-- 수집 완료 시각. 16.21 첫 경기는 UTC 11/4 15:30 = KST 11/5 00:30
INSERT INTO stats_aggregation_completions (completed_at, match_id) VALUES
    ('2026-10-20 03:00+00', '16.20-4-JUNGLE-1'),
    ('2026-11-04 15:30+00', '16.21-1-MIDDLE-1'),
    ('2026-11-06 01:00+00', '16.21-2-TOP-1');
SQL

passed=0
failed=0
check() {
  local name=$1
  shift
  if "$@"; then
    passed=$((passed + 1)); echo "ok   $name"
    return
  fi
  failed=$((failed + 1)); echo "FAIL $name"
}
has() { grep -qF -- "$1" <<< "$2"; }
fails() { ! "$@" > /dev/null 2>&1; }
succeeds() { "$@" > /dev/null; }

status() { "$pipeline/patch-status.sh" "$@"; }

# 1. 현재 패치·경과일·커버리지
out=$(status --date 2026-11-08)
while IFS= read -r line; do echo "     | $line"; done <<< "$out"
check "현재 패치를 숫자 순서로 고른다 (16.9가 아니라 16.21)" has "patch: 16.21" "$out"
check "직전 패치는 16.20이다" has "previous: 16.20" "$out"
check "시작일은 KST 기준이다 (UTC 11/4가 아니라 11/5)" has "started: 2026-11-05" "$out"
check "11/8은 4일차다" has "day: 4" "$out"
check "대상 챔피언은 현재·직전 패치에 나온 챔피언이다" has "champions: 4" "$out"
check "주 포지션 100건 이상 비율 (MIDDLE+MID 합산, 미완료·무포지션·빈 순서 제외)" has "coverage100: 50.0%" "$out"
check "주 포지션 300건 이상 비율" has "coverage300: 25.0%" "$out"

# 2. 재학습 판단을 하지 않는다
check "커버리지가 낮아도 실패하지 않는다 (종료 코드 0)" succeeds status --date 2026-11-08
check "재학습 가능 여부를 출력하지 않는다" fails has "retrain" "$out"
check "하한 옵션은 없다" fails status --min-coverage300 70

# 3. JSON과 입력 검사
json=$(status --date 2026-11-08 --json)
check "JSON으로도 출력한다" has '{"patch":"16.21","previous":"16.20","started":"2026-11-05","day":4,"champions":4,"coverage100":50.0,"coverage300":25.0}' "$json"
check "--patch로 다른 패치를 볼 수 있다" has "patch: 16.20" "$(status --date 2026-10-21 --patch 16.20)"
check "데이터가 없는 패치는 실패한다" fails status --patch 99.1
check "날짜 형식이 틀리면 실패한다" fails status --date 2026/11/08
check "시작일보다 이른 날짜는 실패한다" fails status --date 2026-11-01

# 4. 읽기 전용
before=$(psql -X -A -t -c "SELECT count(*) FROM normalized_match_participants")
status --date 2026-11-08 > /dev/null
check "실행 후 행 수가 그대로다" test "$(psql -X -A -t -c "SELECT count(*) FROM normalized_match_participants")" = "$before"
check "읽기 전용 트랜잭션으로 접속한다" grep -q 'default_transaction_read_only=on' "$pipeline/patch-status.sh"

echo "passed=$passed failed=$failed"
[[ "$failed" == 0 ]]
