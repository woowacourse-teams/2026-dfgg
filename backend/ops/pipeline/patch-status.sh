#!/usr/bin/env bash
# 현재 패치와 커버리지를 출력한다. DB는 읽기만 한다.
# 일정은 야간 워크플로가 정한다(통계 매일, 재학습 화·토). 재학습은 커버리지와 무관하게 돈다.
# 통계·학습이 최근 3패치 창을 쓰고 품질 비교가 배포를 막으므로, 이 값은 판단이 아니라 재학습 기록용이다.
#
#   사용법: patch-status.sh [--date YYYY-MM-DD] [--patch 16.21] [--json]
#   --date   오늘 대신 이 날짜(KST)로 경과일을 계산한다
#   --patch  가장 최근 패치 대신 이 패치를 본다
#
#   접속은 libpq 환경 변수로 받는다(PGSERVICE 또는 PGHOST·PGDATABASE·PGUSER, 비밀번호는 ~/.pgpass).
#   main DB를 가리켜야 한다. 새 패치 데이터가 없으면 실패한다.

set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
date="" patch="" json=false

usage() { echo "usage: $0 [--date YYYY-MM-DD] [--patch MAJOR.MINOR] [--json]" >&2; exit 2; }
fail() { echo "error: $*" >&2; exit 1; }

while (($#)); do
  case "$1" in
    --date) [[ $# -ge 2 ]] || usage; date=$2; shift 2 ;;
    --patch) [[ $# -ge 2 ]] || usage; patch=$2; shift 2 ;;
    --json) json=true; shift ;;
    *) usage ;;
  esac
done
[[ -z "$date" || "$date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || fail "invalid date: $date"
[[ -z "$patch" || "$patch" =~ ^[0-9]+\.[0-9]+$ ]] || fail "invalid patch: $patch"

# 읽기 전용 트랜잭션으로만 접속한다. 쿼리가 실수로 쓰려 해도 DB가 거부한다.
row=$(PGOPTIONS='-c default_transaction_read_only=on' psql -X -q -A -t -v ON_ERROR_STOP=1 \
  -v patch="$patch" -v today="$date" -f "$here/sql/patch-status.sql") || fail "query failed"
IFS='|' read -r current previous started day champions coverage100 coverage300 <<< "$row"
[[ -n "$current" && -n "$started" && "$champions" != 0 ]] || fail "no data for patch ${current:-?}"
(( day >= 1 )) || fail "date is before the patch started ($started)"

if $json; then
  printf '{"patch":"%s","previous":"%s","started":"%s","day":%d,"champions":%d,' \
    "$current" "$previous" "$started" "$day" "$champions"
  printf '"coverage100":%s,"coverage300":%s}\n' "$coverage100" "$coverage300"
  exit 0
fi

cat <<OUT
patch: $current
previous: ${previous:-none}
started: $started
day: $day
champions: $champions
coverage100: ${coverage100}%
coverage300: ${coverage300}%
OUT
