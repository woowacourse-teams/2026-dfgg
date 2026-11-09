#!/usr/bin/env bash
# main의 서빙 테이블을 dev·prod DB로 옮긴다. 대상은 replication-tables.txt의 replicate(바꿔 끼움)·upsert(추가·갱신) 줄이다.
#
#   사용법:
#     replicate.sh dump --out <폴더>                               main에서 한 스냅숏으로 덤프한다
#     replicate.sh load --target dev|prod --dump <폴더> [--dry-run]  덤프를 대상 DB에 교체해 넣는다
#
#   순서: 같은 덤프를 dev에 먼저 넣고, dev가 성공한 덤프만 prod에 넣는다(덤프 폴더의 dev.ok).
#   교체: 대상 DB의 replica_stage 스키마에 같은 정의로 테이블을 만들어 적재 → 인덱스·제약 생성 → VACUUM (ANALYZE)
#         → 한 트랜잭션에서 모든 테이블을 바꿔 끼운다. 중간에 실패하면 기존 테이블이 그대로 남는다.
#   upsert 테이블(카탈로그)은 교체 전에 한 트랜잭션에서 main 행을 추가·갱신한다. 지우지 않는다.
#   원본이 0행인데 대상에 값이 있는 replicate 테이블은 교체하지 않고 멈춘다(원본 쪽 수집이 비었을 가능성).
#   인덱스·제약·외래 키는 대상 DB의 현재 정의(Flyway)를 같은 이름으로 다시 만든다.
#   복제하지 않는 테이블이 복제 대상을 외래 키로 참조하면 안 된다(교체 때 그 제약이 옛 테이블과 함께 지워진다).
#
#   접속: libpq 서비스 이름. main은 SOURCE_PGSERVICE(기본 main), 대상은 --target 값과 같은 서비스 이름.
#   종료 코드: 0 성공, 1 실패, 3 대기(main과 대상의 Flyway 버전이 다름. 코드 배포 중이므로 다음 실행에서 다시 한다)

set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
tables_file="$here/replication-tables.txt"
source_service=${SOURCE_PGSERVICE:-main}
lock_timeout=${SWAP_LOCK_TIMEOUT:-5s}

usage() {
  echo "usage: $0 dump --out <dir> | load --target dev|prod --dump <dir> [--dry-run]" >&2
  exit 2
}
fail() { echo "error: $*" >&2; exit 1; }
log() { echo "$(date +%H:%M:%S) $*"; }

# 대상 DB에 SQL을 보낸다. 비밀번호는 ~/.pgpass에서 읽는다.
db() {
  local service=$1
  shift
  PGSERVICE=$service PGOPTIONS='-c client_min_messages=warning' psql -X -q -A -t -v ON_ERROR_STOP=1 "$@"
}

replicated_tables() {
  awk '$1 == "replicate" { print $2 }' "$tables_file"
}

upserted_tables() {
  awk '$1 == "upsert" { print $2 }' "$tables_file"
}

# 덤프에는 두 종류를 파일 순서대로 담는다.
dumped_tables() {
  awk '$1 == "replicate" || $1 == "upsert" { print $2 }' "$tables_file"
}

flyway_version() {
  db "$1" -c "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
}

columns_of() {
  db "$1" -v table="$2" <<'SQL'
SELECT string_agg(quote_ident(column_name), ',' ORDER BY ordinal_position)
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = :'table' AND is_generated = 'NEVER';
SQL
}

dump() {
  local out=$1 version table columns rows
  [[ -n "$out" ]] || usage
  [[ "$out" != *"'"* ]] || fail "dump path must not contain a quote"
  [[ ! -e "$out" ]] || fail "already exists: $out"
  mkdir -p "$out"
  mapfile -t tables < <(dumped_tables)
  (( ${#tables[@]} > 0 )) || fail "no replicate tables in $tables_file"
  version=$(flyway_version "$source_service")
  [[ -n "$version" ]] || fail "cannot read Flyway version of $source_service"

  # 모든 테이블을 한 스냅숏(REPEATABLE READ)에서 뜬다. 수집이 도는 중에도 테이블 사이 값이 맞는다.
  {
    echo "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;"
    for table in "${tables[@]}"; do
      columns=$(columns_of "$source_service" "$table")
      [[ -n "$columns" ]] || fail "table not found in $source_service: $table"
      echo "$table $columns" >> "$out/columns"
      echo "\\copy (SELECT $columns FROM public.$table) TO PROGRAM 'gzip -1 > ''$out/$table.copy.gz'''"
    done
    echo "COMMIT;"
  } > "$out/dump.sql"
  log "dump: ${#tables[@]} tables from $source_service (flyway $version)"
  db "$source_service" -f "$out/dump.sql"

  {
    echo "flyway $version"
    echo "created $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    for table in "${tables[@]}"; do
      # COPY 텍스트 형식은 값 안의 줄바꿈을 이스케이프하므로 줄 수가 행 수다.
      rows=$(gzip -dc "$out/$table.copy.gz" | wc -l | tr -d ' ')
      echo "table $table $rows"
    done
  } > "$out/manifest"
  rm -f "$out/dump.sql"
  log "dumped ${#tables[@]} tables to $out"
}

manifest_value() { awk -v key="$1" '$1 == key { print $2 }' "$dump_dir/manifest"; }
manifest_rows() { awk -v t="$1" '$1 == "table" && $2 == t { print $3 }' "$dump_dir/manifest"; }
manifest_columns() { awk -v t="$1" '$1 == t { print $2 }' "$dump_dir/columns"; }

# 대상 테이블의 PK·UNIQUE 제약과 그 밖의 인덱스를 replica_stage에 같은 이름으로 만드는 DDL
stage_index_ddl() {
  db "$target" -v table="$1" <<'SQL'
SELECT format('ALTER TABLE replica_stage.%I ADD CONSTRAINT %I %s;', :'table', con.conname, pg_get_constraintdef(con.oid))
FROM pg_constraint con
WHERE con.conrelid = format('public.%I', :'table')::regclass AND con.contype IN ('p', 'u', 'x')
UNION ALL
SELECT replace(pg_get_indexdef(i.indexrelid), format(' ON public.%s ', :'table'), format(' ON replica_stage.%s ', :'table')) || ';'
FROM pg_index i
WHERE i.indrelid = format('public.%I', :'table')::regclass
  AND NOT EXISTS (SELECT 1 FROM pg_constraint con WHERE con.conindid = i.indexrelid);
SQL
}

# 대상 테이블의 외래 키를 replica_stage에 같은 이름으로 만드는 DDL. 복제 대상끼리의 참조는 replica_stage 쪽을
# 가리키게 바꾼다. 교체 때 함께 옮겨지므로 교체 뒤에는 public의 새 테이블을 가리킨다. 카탈로그처럼 복제하지 않는
# 테이블은 public을 그대로 가리킨다. 그 id가 대상 DB에 없으면 제약을 만들 때 실패해 교체 전에 멈춘다.
stage_fk_ddl() {
  local table=$1 name def other
  while IFS='|' read -r name def; do
    [[ -n "$name" ]] || continue
    for other in "${tables[@]}"; do
      def=${def//"REFERENCES public.$other("/"REFERENCES replica_stage.$other("}
      def=${def//"REFERENCES $other("/"REFERENCES replica_stage.$other("}
    done
    echo "ALTER TABLE replica_stage.$table ADD CONSTRAINT \"$name\" $def;"
  done < <(db "$target" -F '|' -v table="$table" <<'SQL'
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid = format('public.%I', :'table')::regclass AND contype = 'f' ORDER BY conname;
SQL
)
}

foreign_key_names() {
  db "$target" -v schema="$1" -v table="$2" <<'SQL'
SELECT coalesce(string_agg(conname, ',' ORDER BY conname), '') FROM pg_constraint
WHERE conrelid = format('%I.%I', :'schema', :'table')::regclass AND contype = 'f';
SQL
}

index_names() {
  db "$target" -v schema="$1" -v table="$2" <<'SQL'
SELECT string_agg(indexname, ',' ORDER BY indexname) FROM pg_indexes WHERE schemaname = :'schema' AND tablename = :'table';
SQL
}

invalid_indexes() {
  db "$target" -v schema="$1" <<'SQL'
SELECT count(*) FROM pg_index i
JOIN pg_class c ON c.oid = i.indrelid JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = :'schema' AND NOT (i.indisvalid AND i.indisready);
SQL
}

# 기본 키가 같으면 갱신하고 없으면 추가한다. 대상에만 있는 행은 남긴다. 모든 카탈로그를 한 트랜잭션에서 맞춘다.
upsert_catalog() {
  local table columns keys updates conflict
  {
    echo "BEGIN;"
    for table in "${upserts[@]}"; do
      columns=$(manifest_columns "$table")
      keys=$(db "$target" -v table="$table" <<'SQL'
SELECT string_agg(quote_ident(a.attname), ',' ORDER BY array_position(i.indkey, a.attnum))
FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
WHERE i.indrelid = format('public.%I', :'table')::regclass AND i.indisprimary;
SQL
)
      [[ -n "$keys" ]] || fail "upsert table needs a primary key: $table"
      updates=$(tr ',' '\n' <<< "$columns" | grep -vxF -f <(tr ',' '\n' <<< "$keys") \
        | awk 'BEGIN { sep = "" } { printf "%s%s = EXCLUDED.%s", sep, $1, $1; sep = ", " }' || true)
      conflict="DO UPDATE SET $updates"
      if [[ -z "$updates" ]]; then conflict="DO NOTHING"; fi
      echo "CREATE TEMP TABLE upsert_$table (LIKE public.$table INCLUDING DEFAULTS) ON COMMIT DROP;"
      echo "\\copy upsert_$table ($columns) FROM PROGRAM 'gzip -dc ''$dump_dir/$table.copy.gz'''"
      echo "INSERT INTO public.$table ($columns) SELECT $columns FROM upsert_$table ON CONFLICT ($keys) $conflict;"
      log "upsert $table ($(manifest_rows "$table") rows)" >&2
    done
    echo "COMMIT;"
  } | db "$target" > /dev/null || fail "catalog upsert failed; nothing was changed"
}

drop_stage() {
  db "$target" -c "DROP SCHEMA IF EXISTS replica_stage CASCADE" > /dev/null 2>&1 || true
}

load() {
  local table expected actual target_version manifest_version
  [[ "$target" =~ ^(dev|prod)$ ]] || usage
  [[ -n "$dump_dir" && -f "$dump_dir/manifest" && -f "$dump_dir/columns" ]] || fail "not a dump: ${dump_dir:-?}"
  [[ "$dump_dir" != *"'"* ]] || fail "dump path must not contain a quote"
  mapfile -t tables < <(replicated_tables)
  mapfile -t upserts < <(upserted_tables)
  [[ "$(awk '$1 == "table" { print $2 }' "$dump_dir/manifest")" == "$(dumped_tables)" ]] \
    || fail "replicate tables changed since the dump; dump again"
  if [[ "$target" == prod && ! -f "$dump_dir/dev.ok" ]]; then
    fail "prod needs a successful dev load of the same dump first ($dump_dir/dev.ok)"
  fi

  manifest_version=$(manifest_value flyway)
  target_version=$(flyway_version "$target")
  if [[ "$target_version" != "$manifest_version" ]]; then
    echo "wait: flyway differs (main $manifest_version, $target ${target_version:-none}). a code deploy is in progress; try next run"
    exit 3
  fi
  for table in "${tables[@]}" "${upserts[@]}"; do
    [[ "$(columns_of "$target" "$table")" == "$(manifest_columns "$table")" ]] || fail "columns differ: $table"
  done
  # 원본이 비었는데 대상에 값이 있으면 지우지 않는다. 원본 쪽 수집·동기화가 아직 안 됐을 가능성이 크다.
  for table in "${tables[@]}"; do
    if [[ "$(manifest_rows "$table")" == 0 && "$(db "$target" -c "SELECT count(*) > 0 FROM public.$table")" == t ]]; then
      fail "$table is empty in the dump but has rows in $target; refusing to clear it"
    fi
  done
  [[ "$(invalid_indexes public)" == 0 ]] || fail "$target has invalid indexes; fix them before replacing tables"
  if $dry_run; then
    for table in "${upserts[@]}"; do log "would upsert $table ($(manifest_rows "$table") rows)"; done
    for table in "${tables[@]}"; do log "would replace $table ($(manifest_rows "$table") rows)"; done
    echo "dry-run: checks passed"
    exit 0
  fi

  declare -A before before_fk
  for table in "${tables[@]}"; do
    before[$table]=$(index_names public "$table")
    before_fk[$table]=$(foreign_key_names public "$table")
  done

  # 0. 카탈로그를 먼저 맞춘다. 바꿔 끼울 테이블의 외래 키가 이 값을 가리킨다.
  if (( ${#upserts[@]} > 0 )); then
    upsert_catalog
  fi

  # 1. 작업용 스키마에 같은 정의로 만들고 적재한다. 인덱스는 적재 뒤에 만든다(더 빠르다).
  trap drop_stage EXIT
  db "$target" -c "DROP SCHEMA IF EXISTS replica_old CASCADE" -c "DROP SCHEMA IF EXISTS replica_stage CASCADE" \
    -c "CREATE SCHEMA replica_stage" > /dev/null
  for table in "${tables[@]}"; do
    log "load $table ($(manifest_rows "$table") rows)"
    {
      echo "CREATE TABLE replica_stage.$table (LIKE public.$table INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING IDENTITY INCLUDING GENERATED INCLUDING STORAGE);"
      echo "\\copy replica_stage.$table ($(manifest_columns "$table")) FROM PROGRAM 'gzip -dc ''$dump_dir/$table.copy.gz'''"
      stage_index_ddl "$table"
    } | db "$target" > /dev/null
    # 자동 증가 값을 적재한 최댓값 뒤로 맞춘다. 교체 뒤 대상 DB에서 넣는 행이 id 충돌을 내지 않게 한다.
    local column
    for column in $(echo "SELECT attname FROM pg_attribute WHERE attrelid = 'replica_stage.$table'::regclass AND attidentity <> ''" | db "$target"); do
      echo "SELECT setval(pg_get_serial_sequence('replica_stage.$table', '$column'), max($column)) FROM replica_stage.$table HAVING max($column) IS NOT NULL" \
        | db "$target" > /dev/null
    done
  done

  # 외래 키는 모든 테이블을 적재한 뒤에 만든다(참조하는 부모가 먼저 있어야 한다).
  for table in "${tables[@]}"; do
    stage_fk_ddl "$table" | db "$target" > /dev/null || fail "foreign key check failed: $table (target catalog may lack ids)"
  done

  # 2. 교체 전 검사: 행 수, 인덱스·외래 키 이름, 인덱스 상태
  for table in "${tables[@]}"; do
    expected=$(manifest_rows "$table")
    actual=$(db "$target" -c "SELECT count(*) FROM replica_stage.$table")
    [[ "$actual" == "$expected" ]] || fail "row count differs: $table (dump $expected, loaded $actual)"
    [[ "$(index_names replica_stage "$table")" == "${before[$table]}" ]] || fail "index names differ: $table"
    [[ "$(foreign_key_names replica_stage "$table")" == "${before_fk[$table]}" ]] || fail "foreign keys differ: $table"
  done
  [[ "$(invalid_indexes replica_stage)" == 0 ]] || fail "invalid index in replica_stage"

  # 3. 새로 넣은 테이블은 visibility map이 비어 Index Only Scan을 못 탄다. ANALYZE만으로는 안 채워진다.
  for table in "${tables[@]}"; do
    db "$target" -c "VACUUM (ANALYZE) replica_stage.$table"
  done

  # 4. 한 트랜잭션에서 바꿔 끼운다. 잠금을 오래 기다리면 요청이 줄줄이 막히므로 짧게 기다리고 다시 시도한다.
  local swap="$dump_dir/.swap-$target.sql" attempt swapped=false
  {
    echo "SET lock_timeout = '$lock_timeout';"
    echo "BEGIN;"
    echo "CREATE SCHEMA replica_old;"
    for table in "${tables[@]}"; do
      echo "ALTER TABLE public.$table SET SCHEMA replica_old;"
      echo "ALTER TABLE replica_stage.$table SET SCHEMA public;"
    done
    echo "COMMIT;"
  } > "$swap"
  for attempt in 1 2 3; do
    log "swap attempt $attempt"
    if db "$target" -f "$swap" > /dev/null; then swapped=true; break; fi
    db "$target" -c "DROP SCHEMA IF EXISTS replica_old" > /dev/null 2>&1 || true
    sleep 5
  done
  rm -f "$swap"
  $swapped || fail "could not acquire locks to swap; previous tables are kept"
  trap - EXIT

  # 5. 정리와 확인. 여기서 실패해도 새 테이블은 이미 서비스 중이다.
  db "$target" -c "DROP SCHEMA replica_old CASCADE" -c "DROP SCHEMA replica_stage CASCADE" > /dev/null \
    || echo "warning: could not drop replica_old/replica_stage; drop them by hand" >&2
  for table in "${tables[@]}"; do
    [[ "$(index_names public "$table")" == "${before[$table]}" ]] || fail "index names changed after swap: $table"
    [[ "$(foreign_key_names public "$table")" == "${before_fk[$table]}" ]] || fail "foreign keys changed after swap: $table"
  done
  [[ "$(invalid_indexes public)" == 0 ]] || fail "invalid index after swap"
  if [[ "$target" == dev ]]; then date -u +%Y-%m-%dT%H:%M:%SZ > "$dump_dir/dev.ok"; fi
  log "load done: $target"
}

command=${1:-}
[[ -n "$command" ]] || usage
shift
out="" target="" dump_dir="" dry_run=false
while (($#)); do
  case "$1" in
    --out) [[ $# -ge 2 ]] || usage; out=$2; shift 2 ;;
    --target) [[ $# -ge 2 ]] || usage; target=$2; shift 2 ;;
    --dump) [[ $# -ge 2 ]] || usage; dump_dir=$2; shift 2 ;;
    --dry-run) dry_run=true; shift ;;
    *) usage ;;
  esac
done
test -r "$tables_file" || fail "not found: $tables_file"

case "$command" in
  dump) dump "$out" ;;
  load) load ;;
  *) usage ;;
esac
