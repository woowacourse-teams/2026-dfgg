#!/usr/bin/env bash
# S3에 올린 덤프를 받아 이 서버의 대상 DB(dev|prod)에 교체해 넣고, 재기동이 필요한지 판단한다.
# 대상 서버에서 실행한다. 접속은 libpq 서비스 이름(--target 값)으로, ~/.pg_service.conf·~/.pgpass에 있어야 한다.
#
#   사용법: load-target.sh --target dev|prod --dump <이름>
#   결과(GITHUB_OUTPUT이 있으면 거기에도 쓴다):
#     status=done|wait   wait는 main과 대상의 Flyway 버전이 달라 이번에는 건너뛴 것(코드 배포 중)
#     restart=yes|no     최근 패치 창이 바뀌었으면 yes. 앱이 기동 때 읽어 둔 창이 낡았으므로 재기동한다
#
#   DUMP_S3_PREFIX  덤프 위치 (기본값 s3://techcourse-project-2026/dfgg/backup)
#   DUMP_ROOT       서버의 덤프 폴더 (기본값 /opt/dfgg/replication)
#   PATCH_WINDOW    최근 패치 창 크기 (기본값 3, 앱의 recommendation.recent-patch-window-size와 같게)

set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
prefix=${DUMP_S3_PREFIX:-s3://techcourse-project-2026/dfgg/backup}
root=${DUMP_ROOT:-/opt/dfgg/replication}
window=${PATCH_WINDOW:-3}
target="" name=""

usage() { echo "usage: $0 --target dev|prod --dump <name>" >&2; exit 2; }
fail() { echo "error: $*" >&2; exit 1; }
output() {
  echo "$1"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then echo "$1" >> "$GITHUB_OUTPUT"; fi
}

while (($#)); do
  case "$1" in
    --target) [[ $# -ge 2 ]] || usage; target=$2; shift 2 ;;
    --dump) [[ $# -ge 2 ]] || usage; name=$2; shift 2 ;;
    *) usage ;;
  esac
done
[[ "$target" =~ ^(dev|prod)$ ]] || usage
[[ "$name" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid dump name: $name"
[[ "$window" =~ ^[1-9][0-9]*$ ]] || fail "invalid PATCH_WINDOW: $window"
command -v psql > /dev/null || fail "psql is not installed"

recent_patches() {
  PGSERVICE=$target psql -X -q -A -t -v ON_ERROR_STOP=1 -v size="$window" -f "$here/sql/recent-patches.sql"
}

dump="$root/$name"
mkdir -p "$root"
rm -rf "$dump"
aws s3 cp --recursive --only-show-errors "$prefix/$name/" "$dump/" || fail "cannot download $prefix/$name/"

before=$(recent_patches)
set +e
"$here/replicate.sh" load --target "$target" --dump "$dump"
code=$?
set -e
if [[ "$code" == 3 ]]; then
  output status=wait
  output restart=no
  exit 0
fi
[[ "$code" == 0 ]] || fail "replicate.sh failed with $code"

after=$(recent_patches)
echo "recent patches: before=[$before] after=[$after]"
output status=done
if [[ "$before" != "$after" ]]; then output restart=yes; else output restart=no; fi

# dev가 성공한 덤프만 prod에 넣을 수 있다. prod 서버가 받을 수 있게 표시를 S3에도 둔다.
if [[ "$target" == dev ]]; then
  aws s3 cp --only-show-errors "$dump/dev.ok" "$prefix/$name/dev.ok"
fi
rm -rf "$dump"
