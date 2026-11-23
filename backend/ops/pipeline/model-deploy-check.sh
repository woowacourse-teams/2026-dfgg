#!/usr/bin/env bash
# 재학습을 통과한 묶음을 배포해도 되는지 확인하고, 배포, 알림에 쓸 값을 낸다 (U12).
#
#   사용법: model-deploy-check.sh --model ltr-YYMMDD[-N]
#   status=ready(배포) | already(current.json이 이미 이 묶음), previous=되돌릴 묶음, summary=비교 요약(여러 줄)을
#   GITHUB_OUTPUT이 있으면 거기에, 없으면 표준 출력에 적는다. 이름이 틀리거나 묶음이 없으면 실패한다.
#   previous는 current.json의 묶음이고, current.json이 없으면 이름 날짜가 가장 최근인 다른 통과 묶음이다(retrain.sh와 같다).
#
#   MODEL_S3_PREFIX  묶음 위치 (기본값 s3://techcourse-project-2026/dfgg/ltr)

set -Eeuo pipefail

prefix=${MODEL_S3_PREFIX:-s3://techcourse-project-2026/dfgg/ltr}
name_pattern='^ltr-[0-9]{6}(-[0-9]+)?$'

usage() { echo "usage: $0 --model ltr-YYMMDD[-N]" >&2; exit 2; }
fail() { echo "error: $*" >&2; exit 1; }

model=""
while (($#)); do
  case "$1" in
    --model) [[ $# -ge 2 ]] || usage; model=$2; shift 2 ;;
    *) usage ;;
  esac
done
[[ "$model" =~ $name_pattern ]] || fail "invalid model bundle name: $model"
aws s3 ls "$prefix/$model/SHA256SUMS" > /dev/null 2>&1 || fail "bundle not found: $prefix/$model/"

current_name() {
  if aws s3 ls "$prefix/current.json" > /dev/null 2>&1; then
    aws s3 cp "$prefix/current.json" - | jq -er '.name | select(type == "string")'
    return
  fi
  aws s3 ls "$prefix/" | awk '$1 == "PRE" { sub("/$", "", $2); print $2 }' | grep -E "$name_pattern" | grep -vxF "$model" \
    | awk -F- '{ n = ($3 == "" ? 1 : $3); printf "%s %06d %s\n", $2, n, $0 }' | sort | tail -1 | cut -d' ' -f3
}

previous=$(current_name || true)
status=ready
if [[ "$previous" == "$model" ]]; then
  status=already
fi
summary=$(aws s3 cp "$prefix/$model/report/compare.json" - 2> /dev/null | jq -r '.summary // empty' || true)
if [[ -z "$summary" ]]; then
  summary="비교 보고서가 없습니다: $prefix/$model/report/compare.json"
fi

out=${GITHUB_OUTPUT:-/dev/stdout}
{
  echo "status=$status"
  echo "previous=$previous"
  echo 'summary<<SUMMARY_EOF'
  echo "$summary"
  echo 'SUMMARY_EOF'
} >> "$out"
