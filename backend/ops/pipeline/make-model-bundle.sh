#!/usr/bin/env bash
# Core 모델과 Component 산출물 폴더로 모델 묶음을 만든다. --upload를 주면 S3에 올린다.
# current.json은 바꾸지 않는다. 포인터 변경은 배포 결정이라 따로 한다.
#
#   사용법: make-model-bundle.sh --name ltr-YYMMDD[-N] --core <model.json> --component <폴더>
#                                [--out <폴더>] [--upload]
#   --component 폴더에서는 model.json과 조회표 두 개만 담는다.
#   이름의 날짜는 묶음에 담은 모델 중 가장 최근에 학습한 모델의 날짜를 쓴다.
#
#   MODEL_S3_PREFIX  올릴 위치 (기본값 s3://techcourse-project-2026/dfgg/ltr)

set -Eeuo pipefail

prefix=${MODEL_S3_PREFIX:-s3://techcourse-project-2026/dfgg/ltr}
component_files=(model.json component-policy-lookup.json situational-component-lookup.json)
name_pattern='^ltr-[0-9]{6}(-[0-9]+)?$'

usage() {
  echo "usage: $0 --name ltr-YYMMDD[-N] --core <model.json> --component <dir> [--out <dir>] [--upload]" >&2
  exit 2
}
fail() { echo "error: $*" >&2; exit 1; }

name="" core="" component="" out="./bundles" upload=false
while (($#)); do
  case "$1" in
    --name) [[ $# -ge 2 ]] || usage; name=$2; shift 2 ;;
    --core) [[ $# -ge 2 ]] || usage; core=$2; shift 2 ;;
    --component) [[ $# -ge 2 ]] || usage; component=$2; shift 2 ;;
    --out) [[ $# -ge 2 ]] || usage; out=$2; shift 2 ;;
    --upload) upload=true; shift ;;
    *) usage ;;
  esac
done
[[ -n "$name" && -n "$core" && -n "$component" ]] || usage
[[ "$name" =~ $name_pattern ]] || fail "invalid bundle name: $name"

jq -e . "$core" > /dev/null || fail "core model is not JSON: $core"
for file in "${component_files[@]}"; do
  jq -e . "$component/$file" > /dev/null || fail "component file missing or not JSON: $component/$file"
done

bundle="$out/$name"
[[ ! -e "$bundle" ]] || fail "already exists: $bundle"
mkdir -p "$bundle/component"
cp "$core" "$bundle/model.json"
for file in "${component_files[@]}"; do
  cp "$component/$file" "$bundle/component/$file"
done

component_name=$(jq -r '.name // empty' "$component/manifest.json" 2> /dev/null || true)
jq -n \
  --arg name "$name" \
  --arg createdAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg coreSource "$core" \
  --arg componentSource "${component_name:-$component}" \
  '{name: $name, kind: "dfgg-model-bundle", createdAt: $createdAt,
    sources: {core: $coreSource, component: $componentSource}}' > "$bundle/manifest.json"

(cd "$bundle" && find . -type f ! -name SHA256SUMS | sed 's#^\./##' | sort | xargs sha256sum > SHA256SUMS)
echo "built $bundle"

if $upload; then
  if aws s3 ls "$prefix/$name/" > /dev/null 2>&1; then
    fail "already uploaded: $prefix/$name/"
  fi
  aws s3 cp --recursive --only-show-errors "$bundle/" "$prefix/$name/"
  echo "uploaded $prefix/$name/"
fi
