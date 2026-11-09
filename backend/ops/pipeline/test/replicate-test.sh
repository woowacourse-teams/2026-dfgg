#!/usr/bin/env bash
# replicate.sh를 로컬 Postgres의 두 데이터베이스(main 역할, 대상 역할)로 시험한다.
# 두 DB 모두 저장소의 Flyway 마이그레이션을 그대로 적용해 실제와 같은 테이블·인덱스를 만든다.
#
#   사용법 (Docker, 저장소 루트에서):
#   docker run --rm -e POSTGRES_PASSWORD=test \
#     -v "$PWD/backend/ops/pipeline:/w:ro" -v "$PWD/backend/dfgg/src/main/resources/db/migration:/m:ro" postgres:16 \
#     bash -c 'docker-entrypoint.sh postgres -c autovacuum=off > /dev/null 2>&1 & until pg_isready -q -h localhost; do sleep 1; done; sleep 2;
#              PGHOST=localhost PGUSER=postgres PGPASSWORD=test MIGRATIONS=/m /w/test/replicate-test.sh'

set -Eeuo pipefail

# autovacuum이 돌면 VACUUM 단계를 빼먹어도 Heap Fetches가 0으로 보인다. 시험은 autovacuum을 끈 서버에서 돌린다.
[[ "$(psql -X -A -t -d postgres -c 'SHOW autovacuum')" == off ]] || { echo "run postgres with -c autovacuum=off" >&2; exit 1; }

here=$(cd "$(dirname "$0")" && pwd)
pipeline=$(dirname "$here")
migrations=${MIGRATIONS:?MIGRATIONS must point to db/migration}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

sql() { psql -X -q -A -t -v ON_ERROR_STOP=1 -d "$1" -c "$2"; }

# 서비스 이름으로 접속한다(실서버의 ~/.pg_service.conf와 같은 방식). dev와 prod는 같은 시험 DB를 가리킨다.
cat > "$work/pg_service.conf" <<EOF
[main]
host=$PGHOST
dbname=repl_main
user=$PGUSER
[dev]
host=$PGHOST
dbname=repl_target
user=$PGUSER
[prod]
host=$PGHOST
dbname=repl_target
user=$PGUSER
EOF
export PGSERVICEFILE="$work/pg_service.conf"

for db in repl_main repl_target; do
  sql postgres "DROP DATABASE IF EXISTS $db" > /dev/null 2>&1
  sql postgres "CREATE DATABASE $db" > /dev/null
  for file in $(find "$migrations" -name "V*.sql" | sort -V); do
    psql -X -q -v ON_ERROR_STOP=1 -d "$db" -f "$file" > /dev/null
  done
  sql "$db" "CREATE TABLE flyway_schema_history (installed_rank int PRIMARY KEY, version varchar(50), success boolean NOT NULL)"
  latest=$(find "$migrations" -name "V*.sql" | sed -E 's#.*/V([0-9]+)__.*#\1#' | sort -n | tail -1)
  sql "$db" "INSERT INTO flyway_schema_history SELECT v, v::text, true FROM generate_series(1, $latest) v"
done

# 카탈로그: 두 DB에 공통으로 넣고, main에만 새 챔피언·아이템과 바뀐 이름을, 대상에만 따로 넣은 챔피언을 둔다.
# 복제는 카탈로그를 지우지 않고 main 값을 추가·갱신한다(대회 픽·조합 통계의 외래 키가 가리키므로 바꿔 끼울 수 없다).
for db in repl_main repl_target; do
  psql -X -q -v ON_ERROR_STOP=1 -d "$db" <<'SQL'
INSERT INTO champions (champion_id, name, riot_key) VALUES (1, '{"ko-KR":"가"}', 'A'), (2, '{"ko-KR":"나"}', 'B');
INSERT INTO items (item_id, name) VALUES (3031, '{"ko-KR":"옛 이름"}');
INSERT INTO runes (rune_id, kind, name) VALUES (8112, 'PERK', '{"ko-KR":"감전"}');
INSERT INTO summoner_spells (spell_id, riot_key, name, icon_key) VALUES (4, 'SummonerFlash', '{"ko-KR":"점멸"}', 'flash');
SQL
done
psql -X -q -v ON_ERROR_STOP=1 -d repl_main <<'SQL'
INSERT INTO champions (champion_id, name, riot_key) VALUES (3, '{"ko-KR":"새 챔피언"}', 'C');
INSERT INTO items (item_id, name) VALUES (3006, '{"ko-KR":"광전사의 군화"}');
UPDATE items SET name = '{"ko-KR":"무한의 대검"}' WHERE item_id = 3031;
INSERT INTO champion_tags (champion_id, tag) VALUES (1, 'MAGE'), (3, 'FIGHTER');
INSERT INTO champion_counters (enemy_champion_id, position, counter_champion_id, rank) VALUES (1, 'MID', 2, 1), (1, 'MID', 3, 2);
INSERT INTO aram_mayhem_augments (patch, augment_id, name, rarity) VALUES ('16.22', 1, '{"ko-KR":"증강"}', 'GOLD');
INSERT INTO aram_mayhem_champion_augments (patch, champion_id, augment_id, games, pick_rate) VALUES ('16.22', 1, 1, 100, 12.5);
SQL
psql -X -q -v ON_ERROR_STOP=1 -d repl_target <<'SQL'
INSERT INTO champions (champion_id, name, riot_key) VALUES (99, '{"ko-KR":"대상에만"}', 'Z');
INSERT INTO champion_tags (champion_id, tag) VALUES (2, 'TANK');
INSERT INTO champion_counters (enemy_champion_id, position, counter_champion_id, rank) VALUES (2, 'TOP', 1, 1);
INSERT INTO aram_mayhem_augments (patch, augment_id, name, rarity) VALUES ('16.21', 9, '{"ko-KR":"옛 증강"}', 'SILVER');
SQL

# main: 수집·집계된 값
psql -X -q -v ON_ERROR_STOP=1 -d repl_main <<'SQL'
INSERT INTO normalized_match_participants (champion_id, core_item_purchase_order, core_item_purchase_order_complete,
    final_core_item_ids, match_id, participant_id, patch, position, puuid, queue_id, team_id, win, tier)
SELECT 1 + g % 20, '3031,3006,3094', g % 3 <> 0, '3031', 'KR_' || g, 1 + g % 10, '16.21',
       (ARRAY['TOP','JUNGLE','MIDDLE','BOTTOM','UTILITY'])[1 + g % 5], 'p' || g, 420, 100, g % 2 = 0, 'EMERALD'
FROM generate_series(1, 3000) g;
INSERT INTO normalized_match_participant_runes (match_id, participant_id, patch, champion_id, team_id, position, win,
    primary_style_id, keystone_id, primary_perk1_id, primary_perk2_id, primary_perk3_id, sub_style_id, sub_perk1_id,
    sub_perk2_id, stat_offense_id, stat_flex_id, stat_defense_id)
SELECT 'KR_' || g, 1, '16.21', 1, 100, 'MIDDLE', true, 8100, 8112, 8126, 8138, 8135, 8300, 8304, 8345, 5005, 5008, 5001
FROM generate_series(1, 500) g;
INSERT INTO champion_position_stats (patch, champion_id, position, games)
SELECT '16.21', g, 'MID', 100 + g FROM generate_series(1, 10) g;
INSERT INTO champion_item_stats (champion_game_count_all, champion_game_count_recent, champion_id, item_id, position,
    purchase_count_all, purchase_count_recent, win_count_all, win_count_recent, champion_win_count_all)
SELECT 1000, 300, g, 3031, 'MID', 500, 150, 260, 80, 520 FROM generate_series(1, 20) g;
SQL

# main: 대회 픽 (서로, 그리고 카탈로그를 외래 키로 참조한다)
psql -X -q -v ON_ERROR_STOP=1 -d repl_main <<'SQL'
INSERT INTO pro_tournaments (overview_page, name, league) VALUES ('LCK/2026', 'LCK 2026', 'LCK');
INSERT INTO pro_players (leaguepedia_page, name, updated_at) VALUES ('Faker', 'Faker', now()), ('Chovy', 'Chovy', now());
INSERT INTO pro_picks (tournament_id, player_id, source_game_id, played_at, team, side, position, champion_id, win)
SELECT 1, p, 'G' || g, now(), 'T1', 'BLUE', 'MID', 1 + g % 3, true FROM generate_series(1, 20) g, generate_series(1, 2) p;
INSERT INTO pro_pick_items (pick_id, slot, item_id) SELECT id, 0, 3031 FROM pro_picks;
INSERT INTO pro_pick_items (pick_id, slot, item_id) SELECT id, 1, 3006 FROM pro_picks WHERE id % 4 = 0;
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) SELECT id, 0, 8112 FROM pro_picks;
INSERT INTO pro_pick_spells (pick_id, slot, spell_id) SELECT id, 0, 4 FROM pro_picks;
SQL

# 대상: 예전 값과, 대상이 직접 쓰는 feedback
psql -X -q -v ON_ERROR_STOP=1 -d repl_target <<'SQL'
INSERT INTO normalized_match_participants (champion_id, core_item_purchase_order, core_item_purchase_order_complete,
    final_core_item_ids, match_id, participant_id, patch, position, puuid, queue_id, team_id, win, tier)
SELECT 1, '1001', true, '1001', 'OLD_' || g, 1, '16.20', 'TOP', 'old' || g, 420, 100, true, 'EMERALD'
FROM generate_series(1, 5) g;
INSERT INTO champion_position_stats (patch, champion_id, position, games) VALUES ('16.20', 99, 'TOP', 1);
INSERT INTO feedback (feedback_id, content, date) VALUES (1, 'keep me', '2026-11-01');
INSERT INTO pro_tournaments (overview_page, name, league) VALUES ('OLD/2025', 'OLD', 'OLD');
INSERT INTO pro_players (leaguepedia_page, name, updated_at) VALUES ('Old', 'Old', now());
INSERT INTO pro_picks (tournament_id, player_id, source_game_id, played_at, team, side, position, champion_id, win)
VALUES (1, 1, 'OLD1', now(), 'OLD', 'RED', 'TOP', 2, false);
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
eq() { [[ "$1" == "$2" ]] || { echo "     expected [$2] got [$1]"; return 1; }; }
has() { grep -qF -- "$1" <<< "$2"; }
target() { sql repl_target "$1"; }
indexes() { target "SELECT string_agg(indexname, ',' ORDER BY indexname) FROM pg_indexes WHERE schemaname = 'public' AND tablename IN ('normalized_match_participants','normalized_match_participant_runes','champion_position_stats','champion_item_stats')"; }
replicate() { "$pipeline/replicate.sh" "$@"; }

indexes_before=$(indexes)

# 1. 덤프
dump="$work/dump-1"
out=$(replicate dump --out "$dump" 2>&1)
check "덤프가 끝난다" has "dumped 21 tables" "$out"
check "덤프에 main의 Flyway 버전과 행 수가 남는다" has "normalized_match_participants 3000" "$(cat "$dump/manifest")"

# 2. dry-run은 대상을 바꾸지 않는다
out=$(replicate load --target dev --dump "$dump" --dry-run 2>&1)
check "dry-run은 검사만 하고 끝난다" has "dry-run: checks passed" "$out"
check "dry-run 뒤 대상은 그대로다" eq "$(target 'SELECT count(*) FROM normalized_match_participants')" 5

# 3. prod는 같은 덤프의 dev 복제가 먼저 성공해야 한다
check "dev 복제 전에는 prod 복제를 거부한다" bash -c "! '$pipeline/replicate.sh' load --target prod --dump '$dump' > /dev/null 2>&1"

# 4. dev 복제
out=$(replicate load --target dev --dump "$dump" 2>&1)
while IFS= read -r line; do echo "     | $line"; done <<< "$out"
check "dev 복제가 성공한다" has "load done: dev" "$out"
check "대회 픽이 main 값으로 바뀐다" eq "$(target 'SELECT count(*) FROM pro_picks')" 40
check "대회 픽 아이템도 함께 바뀐다" eq "$(target 'SELECT count(*) FROM pro_pick_items')" 50
check "예전 대회는 사라진다" eq "$(target "SELECT count(*) FROM pro_tournaments WHERE league = 'OLD'")" 0
check "외래 키가 같은 이름으로 다시 생긴다" eq "$(target "SELECT string_agg(conname, ',' ORDER BY conname) FROM pg_constraint WHERE contype = 'f' AND conrelid = 'pro_picks'::regclass")" "pro_picks_champion_id_fkey,pro_picks_player_id_fkey,pro_picks_tournament_id_fkey"
check "외래 키가 public의 새 테이블을 가리킨다" eq "$(target "SELECT count(*) FROM pg_constraint WHERE contype = 'f' AND conrelid = 'pro_pick_items'::regclass AND confrelid IN ('public.pro_picks'::regclass, 'public.items'::regclass)")" 2
check "외래 키가 실제로 막는다" bash -c "! psql -X -q -d repl_target -c \"INSERT INTO pro_pick_items VALUES (999999, 0, 3031)\" > /dev/null 2>&1"
check "main에만 있던 챔피언이 추가된다 (대회 픽보다 먼저)" eq "$(target "SELECT name->>'ko-KR' FROM champions WHERE champion_id = 3")" "새 챔피언"
check "바뀐 아이템 이름이 갱신된다" eq "$(target "SELECT name->>'ko-KR' FROM items WHERE item_id = 3031")" "무한의 대검"
check "대상에만 있던 카탈로그 행은 지우지 않는다" eq "$(target 'SELECT count(*) FROM champions WHERE champion_id = 99')" 1
check "챔피언 태그가 main 값으로 바뀐다" eq "$(target "SELECT string_agg(champion_id || ':' || tag, ',' ORDER BY champion_id) FROM champion_tags")" "1:MAGE,3:FIGHTER"
check "카운터가 main 값으로 바뀐다" eq "$(target "SELECT string_agg(enemy_champion_id || position || counter_champion_id, ',' ORDER BY rank) FROM champion_counters")" "1MID2,1MID3"
check "칼바람 증강이 main 값으로 바뀐다" eq "$(target "SELECT string_agg(patch || '/' || augment_id, ',') FROM aram_mayhem_augments")" "16.22/1"
check "칼바람 픽률이 함께 바뀐다" eq "$(target 'SELECT count(*) FROM aram_mayhem_champion_augments')" 1
check "참가자가 main 값으로 바뀐다" eq "$(target 'SELECT count(*) FROM normalized_match_participants')" 3000
check "룬 기록이 바뀐다" eq "$(target 'SELECT count(*) FROM normalized_match_participant_runes')" 500
check "예전 라인 분포는 사라진다" eq "$(target "SELECT count(*) FROM champion_position_stats WHERE patch = '16.20'")" 0
check "대상이 쓰는 feedback은 그대로다" eq "$(target 'SELECT content FROM feedback')" "keep me"
check "인덱스 이름이 교체 전과 같다" eq "$(indexes)" "$indexes_before"
check "커버링 인덱스가 남아 있다" eq "$(target "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_nmp_build_transition' AND schemaname = 'public'")" 1
check "모든 인덱스가 정상이다" eq "$(target "SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indrelid JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND NOT (i.indisvalid AND i.indisready)")" 0
check "작업용 스키마가 남지 않는다" eq "$(target "SELECT count(*) FROM pg_namespace WHERE nspname IN ('replica_stage','replica_old')")" 0
check "id 자동 증가가 이어진다" eq "$(target "INSERT INTO champion_position_stats (patch, champion_id, position, games) VALUES ('t', 1, 'TOP', 1) RETURNING id > 10")" t
target "DELETE FROM champion_position_stats WHERE patch = 't'"
plan=$(target "SET enable_seqscan = off; SET enable_bitmapscan = off; EXPLAIN (ANALYZE, COSTS OFF) SELECT core_item_purchase_order, patch, win FROM normalized_match_participants WHERE champion_id = 3 AND position = 'MIDDLE' AND core_item_purchase_order_complete")
check "조회 대상 행이 있다 (Heap Fetches 0이 빈 결과 때문이 아니다)" has "rows=100 " "$plan"
check "전이 쿼리가 Index Only Scan이다" has "Index Only Scan using idx_nmp_build_transition" "$plan"
check "VACUUM이 끝나 Heap Fetches가 0이다" has "Heap Fetches: 0" "$plan"

# 5. 같은 덤프의 prod 복제
check "dev가 성공한 덤프는 prod 복제가 된다" has "load done: prod" "$(replicate load --target prod --dump "$dump" 2>&1)"

# 6. Flyway 버전이 다르면 대기
target "INSERT INTO flyway_schema_history VALUES (999, '999', true)"
set +e
out=$(replicate load --target dev --dump "$dump" 2>&1)
code=$?
set -e
check "Flyway 버전이 다르면 교체하지 않고 대기(종료 코드 3)로 끝난다" eq "$code" 3
check "대기 메시지를 남긴다" has "wait:" "$out"
target "DELETE FROM flyway_schema_history WHERE installed_rank = 999"

# 7. 적재 중 실패하면 기존 테이블이 남는다
sql repl_main "INSERT INTO champion_position_stats (patch, champion_id, position, games) VALUES ('16.21', 11, 'TOP', 7)"
broken="$work/dump-2"
replicate dump --out "$broken" > /dev/null 2>&1
printf 'not gzip' > "$broken/normalized_match_participants.copy.gz"
check "덤프가 깨지면 복제가 실패한다" bash -c "! '$pipeline/replicate.sh' load --target dev --dump '$broken' > /dev/null 2>&1"
check "실패해도 기존 참가자가 남는다" eq "$(target 'SELECT count(*) FROM normalized_match_participants')" 3000
check "실패해도 다른 테이블도 바뀌지 않는다 (한꺼번에 교체)" eq "$(target 'SELECT count(*) FROM champion_position_stats')" 10
check "실패 뒤 작업용 스키마가 남지 않는다" eq "$(target "SELECT count(*) FROM pg_namespace WHERE nspname IN ('replica_stage','replica_old')")" 0

# 7-1. 원본이 비었는데 대상에 값이 있으면 지우지 않는다 (main 칼바람 동기화가 아직 안 된 경우 등)
sql repl_main "DELETE FROM aram_mayhem_augments"
empty="$work/dump-empty"
replicate dump --out "$empty" > /dev/null 2>&1
out=$(replicate load --target dev --dump "$empty" 2>&1 || true)
check "원본이 빈 테이블로 대상을 비우지 않는다" has "empty in the dump" "$out"
check "그 경우 대상 칼바람 값이 남는다" eq "$(target 'SELECT count(*) FROM aram_mayhem_augments')" 1
sql repl_main "INSERT INTO aram_mayhem_augments (patch, augment_id, name, rarity) VALUES ('16.22', 1, '{\"ko-KR\":\"증강\"}', 'GOLD')"
sql repl_main "INSERT INTO aram_mayhem_champion_augments (patch, champion_id, augment_id, games, pick_rate) VALUES ('16.22', 1, 1, 100, 12.5)"

# 8. load-target.sh: S3에서 받아 넣고, 최근 패치 창이 바뀌면 재기동을 알린다 (가짜 aws가 로컬 폴더를 버킷처럼 쓴다)
bucket="$work/bucket"
mkdir -p "$bucket" "$work/bin"
cat > "$work/bin/aws" <<EOF
#!/usr/bin/env bash
set -euo pipefail
[[ "\$1 \$2" == "s3 cp" ]] || exit 2
shift 2
[[ "\$1" == --recursive ]] && shift
[[ "\$1" == --only-show-errors ]] && shift
src=\${1/s3:\/\/test-bucket/$bucket} dst=\${2/s3:\/\/test-bucket/$bucket}
if [[ -d "\$src" ]]; then mkdir -p "\$dst"; cp -R "\$src"/. "\$dst"/; exit 0; fi
[[ -f "\$src" ]] || exit 1
mkdir -p "\$(dirname "\$dst")"; cp "\$src" "\$dst"
EOF
chmod +x "$work/bin/aws"
load_target() {
  PATH="$work/bin:$PATH" DUMP_S3_PREFIX=s3://test-bucket DUMP_ROOT="$work/root" "$pipeline/load-target.sh" "$@" 2>&1
}
sql repl_main "UPDATE normalized_match_participants SET patch = '16.22' WHERE id % 10 = 0"
replicate dump --out "$bucket/nightly-1" > /dev/null 2>&1
out=$(load_target --target dev --dump nightly-1)
check "load-target이 dev에 넣는다" has "status=done" "$out"
check "새 패치가 생기면 재기동을 알린다" has "restart=yes" "$out"
check "dev 성공 표시를 S3에 남긴다" test -f "$bucket/nightly-1/dev.ok"
check "같은 덤프의 prod는 S3의 dev 성공 표시로 진행된다" has "status=done" "$(load_target --target prod --dump nightly-1)"
out=$(load_target --target dev --dump nightly-1)
check "패치 창이 그대로면 재기동하지 않는다" has "restart=no" "$out"
target "INSERT INTO flyway_schema_history VALUES (999, '999', true)"
out=$(load_target --target dev --dump nightly-1)
check "버전이 다르면 대기로 끝나고 실패하지 않는다" has "status=wait" "$out"
target "DELETE FROM flyway_schema_history WHERE installed_rank = 999"

echo "passed=$passed failed=$failed"
[[ "$failed" == 0 ]]
