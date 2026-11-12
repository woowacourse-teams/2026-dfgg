#!/usr/bin/env bash
# S3의 모델 묶음을 받아 해시를 검증한 뒤 /opt/dfgg/models/<이름>/에 설치한다.
# 마지막 줄에 설치 경로를 출력한다. 이미 설치돼 있고 해시가 맞으면 다시 받지 않는다.
#
#   사용법: fetch-model.sh [--name ltr-YYMMDD[-N]] [--dry-run]
#   --name 없으면 current.json이 가리키는 묶음을 받는다.
#
#   MODEL_S3_PREFIX  묶음 위치 (기본값 s3://techcourse-project-2026/dfgg/ltr)
#   MODEL_ROOT       설치 위치 (기본값 /opt/dfgg/models)
#   SLOT_ROOT        슬롯 위치. <슬롯>/model이 가리키는 묶음은 지우지 않는다 (기본값 /opt/dfgg)
#   MODEL_KEEP       남길 묶음 수. 사용 중인 묶음은 따로 남는다 (기본값 3)

set -Eeuo pipefail

prefix=${MODEL_S3_PREFIX:-s3://techcourse-project-2026/dfgg/ltr}
root=${MODEL_ROOT:-/opt/dfgg/models}
slots=${SLOT_ROOT:-/opt/dfgg}
keep=${MODEL_KEEP:-3}
required=(model.json component/model.json component/component-policy-lookup.json
  component/situational-component-lookup.json)
name_pattern='^ltr-[0-9]{6}(-[0-9]+)?$'

usage() { echo "usage: $0 [--name ltr-YYMMDD[-N]] [--dry-run]" >&2; exit 2; }
fail() { echo "error: $*" >&2; exit 1; }

name=""
dry_run=false
while (($#)); do
  case "$1" in
    --name) [[ $# -ge 2 ]] || usage; name=$2; shift 2 ;;
    --dry-run) dry_run=true; shift ;;
    *) usage ;;
  esac
done

current_name() {
  aws s3 cp "$prefix/current.json" - | jq -er '.name | select(type == "string")'
}

# 필수 파일이 모두 있고 SHA256SUMS에 올라 있으며, 목록의 해시가 모두 맞아야 한다.
verify() {
  local dir=$1 file listed
  test -s "$dir/SHA256SUMS" || { echo "SHA256SUMS missing" >&2; return 1; }
  # 목록을 변수에 담아 검사한다. awk | grep -q는 grep이 먼저 끝나면 SIGPIPE로 판정이 뒤집힌다.
  listed=$(awk '{ print $2 }' "$dir/SHA256SUMS")
  if grep -qE '^/|(^|/)\.\.(/|$)' <<< "$listed"; then
    echo "SHA256SUMS has a path outside the bundle" >&2; return 1
  fi
  for file in "${required[@]}"; do
    test -s "$dir/$file" || { echo "missing $file" >&2; return 1; }
    grep -qxF "$file" <<< "$listed" || { echo "$file not in SHA256SUMS" >&2; return 1; }
    jq -e . "$dir/$file" > /dev/null || { echo "$file is not JSON" >&2; return 1; }
  done
  (cd "$dir" && sha256sum --quiet --strict -c SHA256SUMS)
}

in_use() {
  local link
  for link in "$slots"/*/model; do
    [[ -L "$link" ]] && readlink -f "$link"
  done
  return 0
}

# 묶음 규칙에 맞는 폴더만 오래된 순으로 지운다. 사용 중인 묶음은 개수에서도 빼고 남긴다.
prune() {
  local used candidates dir
  used=$(in_use)
  candidates=()
  while IFS= read -r dir; do
    [[ "$(basename "$dir")" =~ $name_pattern ]] || continue
    grep -qxF "$(readlink -f "$dir")" <<< "$used" && continue
    candidates+=("$dir")
  done < <(ls -1dt "$root"/*/ 2> /dev/null | sed 's#/$##')
  for dir in "${candidates[@]:$keep}"; do
    echo "remove $dir"
    $dry_run || rm -rf -- "$dir"
  done
}

if [[ -z "$name" ]]; then
  name=$(current_name) || fail "cannot read $prefix/current.json"
fi
[[ "$name" =~ $name_pattern ]] || fail "invalid bundle name: $name"
target="$root/$name"

if $dry_run; then
  echo "bundle: $prefix/$name/"
  aws s3 ls "$prefix/$name/" > /dev/null || fail "bundle not found: $prefix/$name/"
  echo "would install to $target"
  prune
  exit 0
fi

mkdir -p "$root"
exec 9> "$root/.fetch.lock"
flock -w 600 9 || fail "another fetch is running"

if [[ -d "$target" ]]; then
  verify "$target" > /dev/null || fail "installed bundle is broken: $target"
  echo "already installed"
  echo "$target"
  exit 0
fi

staging=$(mktemp -d "$root/.staging-$name.XXXXXX")
trap 'rm -rf -- "$staging"' EXIT
aws s3 cp --recursive --only-show-errors "$prefix/$name/" "$staging/" || fail "download failed: $prefix/$name/"
verify "$staging" || fail "verification failed: $name"
# 파일은 읽기 전용으로 둔다. 폴더는 쓸 수 있어야 정리 때 지워진다.
find "$staging" -type f -exec chmod 0444 {} +
find "$staging" -type d -exec chmod 0755 {} +
mv -T "$staging" "$target"
trap - EXIT
echo "installed $name"
prune
echo "$target"
