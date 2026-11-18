#!/usr/bin/env bash
# ml 서버에서 Core 모델을 재학습해 현재 운영 모델과 비교하고 S3에 올린다 (U11).
# export(main 읽기 전용) → 학습(game train) → 행동 모델 → 비교(U10) → Java parity → 묶음 → S3.
# 통과하면 ltr/<이름>/, 기준 미달·표본 부족이면 ltr/<이름>-rejected/에 올린다. current.json은 바꾸지 않는다.
# Component·조회표와 게임 상황 모델(v5, 있으면)은 재학습하지 않고 현재 운영 묶음의 것을 그대로 담는다.
# 현재 운영 묶음은 current.json이 가리키는 묶음이고, current.json이 없으면 이름 날짜가 가장 최근인 통과 묶음이다.
#
#   사용법: retrain.sh --work <폴더> [--queries N]
#   마지막 줄에 결과 JSON 경로를 출력한다. 비교 결과와 무관하게 끝까지 가면 0, 중간에 실패하면 0이 아니다.
#
#   EVALUATION_DB_URL·EVALUATION_DB_USERNAME·EVALUATION_DB_PASSWORD  main 읽기 전용 접속 (필수)
#   MODEL_S3_PREFIX  묶음 위치 (기본값 s3://techcourse-project-2026/dfgg/ltr)
#   REPO_ROOT        저장소 위치 (기본값 이 스크립트 기준)
#   GRADLE, UV       명령 (기본값 ./gradlew, uv. 시험에서 바꾼다)
#   GITHUB_OUTPUT    있으면 name·verdict·s3를 적는다

set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
repo=${REPO_ROOT:-$(cd "$here/../../.." && pwd)}
prefix=${MODEL_S3_PREFIX:-s3://techcourse-project-2026/dfgg/ltr}
gradle=${GRADLE:-./gradlew}
uv=${UV:-uv}
name_pattern='^ltr-[0-9]{6}(-[0-9]+)?$'

usage() { echo "usage: $0 --work <dir> [--queries N]" >&2; exit 2; }
fail() { echo "error: $*" >&2; exit 1; }
step() { echo "== $* ($(date -u +%H:%M:%SZ))"; }

work="" queries=30000
while (($#)); do
  case "$1" in
    --work) [[ $# -ge 2 ]] || usage; work=$2; shift 2 ;;
    --queries) [[ $# -ge 2 ]] || usage; queries=$2; shift 2 ;;
    *) usage ;;
  esac
done
[[ -n "$work" ]] || usage
[[ "$queries" =~ ^[1-9][0-9]*$ ]] || fail "invalid --queries: $queries"
for var in EVALUATION_DB_URL EVALUATION_DB_USERNAME EVALUATION_DB_PASSWORD; do
  [[ -n "${!var:-}" ]] || fail "$var is not set"
done

mkdir -p "$work"
work=$(cd "$work" && pwd)
[[ -z "$(ls -A "$work")" ]] || fail "work dir is not empty: $work"
data="$work/data"
mkdir -p "$data" "$work/candidate" "$work/report"

# current.json이 있으면 그 이름, 없으면 이름 날짜가 가장 최근인 묶음(-rejected 제외)을 쓴다.
current_name() {
  local listing
  if aws s3 ls "$prefix/current.json" > /dev/null 2>&1; then
    aws s3 cp "$prefix/current.json" - | jq -er '.name | select(type == "string")'
    return
  fi
  listing=$(aws s3 ls "$prefix/" | awk '$1 == "PRE" { sub("/$", "", $2); print $2 }' | grep -E "$name_pattern" || true)
  [[ -n "$listing" ]] || return 1
  # ltr-YYMMDD 뒤의 -N을 숫자로 비교한다. 번호가 없으면 1번째다.
  awk -F- '{ n = ($3 == "" ? 1 : $3); printf "%s %06d %s\n", $2, n, $0 }' <<< "$listing" | sort | tail -1 | cut -d' ' -f3
}

# 현재 운영 묶음을 받아 해시를 확인한다. 비교 기준이자 Component·게임 상황 모델의 출처다.
fetch_current() {
  local current
  current=$(current_name) || fail "cannot find the current bundle under $prefix/"
  [[ "$current" =~ $name_pattern ]] || fail "invalid current bundle name: $current"
  aws s3 cp --recursive --only-show-errors "$prefix/$current/" "$work/current/" \
    || fail "download failed: $prefix/$current/"
  (cd "$work/current" && sha256sum --quiet --strict -c SHA256SUMS) || fail "current bundle hash mismatch: $current"
  echo "$current"
}

# 오늘 날짜(KST)로 이름을 정한다. 통과·탈락 어느 쪽으로든 이미 쓴 이름이면 -2, -3을 붙인다.
next_name() {
  local base listing candidate n
  base="ltr-$(TZ=Asia/Seoul date +%y%m%d)"
  listing=$(aws s3 ls "$prefix/" | awk '$1 == "PRE" { sub("/$", "", $2); print $2 }')
  candidate=$base
  n=1
  while grep -qxF -e "$candidate" -e "$candidate-rejected" <<< "$listing"; do
    n=$((n + 1))
    candidate="$base-$n"
  done
  echo "$candidate"
}

step "fetch current bundle"
current=$(fetch_current | tail -1)
echo "current: $current"

step "export training set (queries=$queries)"
# 야간 집계가 막 끝난 main의 통계를 그대로 쓴다. 다시 집계하면 main에 쓰게 된다.
(cd "$repo/backend/dfgg" && SPRING_FLYWAY_ENABLED=false "$gradle" evaluationTest --tests '*TrainingSetExportTest' \
  -Devaluation.aggregate=false \
  -Devaluation.queries="$queries" \
  -Devaluation.export.path="$data/train.jsonl")
test -s "$data/train.jsonl" || fail "export produced no rows"
test -s "$data/feature_schema.json" || fail "export produced no feature schema"
report_md="$repo/backend/tasks/eval-training-set.md"
if [[ -f "$report_md" ]]; then
  cp "$report_md" "$work/report/export.md"
fi

cd "$repo/backend/ml"
step "train candidate (game train, baseline contract)"
"$uv" run python -m dfgg_ltr.train --data "$data/train.jsonl" --schema "$data/feature_schema.json" \
  --split game --baseline --out "$work/candidate/model.json" | tee "$work/report/train.log"

step "train behavior model"
"$uv" run python -m dfgg_ltr.propensity --data "$data/train.jsonl" --schema "$data/feature_schema.json" \
  --split game --out-dir "$work/propensity"
cp "$work/propensity/propensity-game-report.json" "$work/report/" 2> /dev/null || true

step "compare with $current"
code=0
"$uv" run python -m dfgg_ltr.compare_models --data "$data/train.jsonl" --schema "$data/feature_schema.json" \
  --current "$work/current/model.json" --candidate "$work/candidate/model.json" \
  --propensity-dir "$work/propensity" --out "$work/report/compare.json" || code=$?
case "$code" in
  0) verdict=passed ;;
  1) verdict=rejected ;;
  2) verdict=insufficient ;;
  *) fail "compare_models failed with exit code $code" ;;
esac
echo "verdict: $verdict"

step "check Java parity"
"$uv" run python -m dfgg_ltr.export_parity_sample --model "$work/candidate/model.json" \
  --data "$data/train.jsonl" --schema "$data/feature_schema.json" --out "$work/candidate/parity.json" --count 1000
(cd "$repo/backend/dfgg" && "$gradle" evaluationTest --tests '*ExternalCoreModelParityTest' \
  -Devaluation.core.model="$work/candidate/model.json" \
  -Devaluation.core.parity="$work/candidate/parity.json" \
  -Devaluation.core.contract=baseline)

step "build bundle"
name=$(next_name)
carried=()
if [[ -f "$work/current/game-situation/model.json" ]]; then
  carried=(--game-situation "$work/current/game-situation/model.json")
fi
"$here/make-model-bundle.sh" --name "$name" --core "$work/candidate/model.json" \
  --component "$work/current/component" ${carried[@]+"${carried[@]}"} --out "$work/bundles"
destination="$name"
[[ "$verdict" == passed ]] || destination="$name-rejected"
# 보고서는 묶음 해시 목록 밖에 둔다. 서버가 받을 때 검증하는 파일이 아니다.
cp -R "$work/report" "$work/bundles/$name/report"

step "upload $destination"
if aws s3 ls "$prefix/$destination/" > /dev/null 2>&1; then
  fail "already uploaded: $prefix/$destination/"
fi
aws s3 cp --recursive --only-show-errors "$work/bundles/$name/" "$prefix/$destination/"

result="$work/result.json"
jq -n --arg name "$destination" --arg verdict "$verdict" --arg current "$current" \
  --arg s3 "$prefix/$destination/" --argjson queries "$queries" \
  '{name: $name, verdict: $verdict, current: $current, s3: $s3, queries: $queries}' > "$result"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "name=$destination"
    echo "verdict=$verdict"
    echo "s3=$prefix/$destination/"
  } >> "$GITHUB_OUTPUT"
fi
echo "$result"
