#!/usr/bin/env bash
# retrain.sh·training-instance.sh·ml/idle-stop.sh를 AWS·DB·학습 없이 시험한다.
# 가짜 aws가 로컬 폴더를 버킷처럼 쓰고, 가짜 gradle·uv가 산출물만 만든다.
#
#   사용법: backend/ops/pipeline/test/retrain-test.sh

set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
pipeline=$(dirname "$here")
ops=$(dirname "$pipeline")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

bucket="$work/bucket"
calls="$work/calls.log"
export MODEL_S3_PREFIX="s3://test-bucket/dfgg/ltr"
export PATH="$work/bin:$PATH"
export REPO_ROOT="$work/repo"
export GRADLE="$work/bin/fake-gradle"
export UV="$work/bin/fake-uv"
export CALLS="$calls"
export EVALUATION_DB_URL="jdbc:postgresql://main.example/dfgg"
export EVALUATION_DB_USERNAME="dfgg_ml"
export EVALUATION_DB_PASSWORD="not-a-secret"
mkdir -p "$bucket/dfgg/ltr" "$work/bin" "$REPO_ROOT/backend/dfgg" "$REPO_ROOT/backend/ml" "$REPO_ROOT/backend/tasks"

# s3 cp·ls와 ec2 상태만 흉내 낸다. 디렉터리 ls는 실제 aws처럼 "PRE 이름/"으로 출력한다.
cat > "$work/bin/aws" <<EOF
#!/usr/bin/env bash
set -euo pipefail
local_path() { echo "$bucket/\${1#s3://test-bucket/}"; }
state_file="$work/ec2-state"
if [[ "\$1" == ec2 ]]; then
  echo "aws \$*" >> "$calls"
  case "\$2" in
    describe-instances)
      current=\$(head -1 "\$state_file")
      # 다음 상태가 적혀 있으면 한 번 읽을 때마다 한 줄씩 넘어간다.
      if [[ \$(wc -l < "\$state_file") -gt 1 ]]; then tail -n +2 "\$state_file" > "\$state_file.next"; mv "\$state_file.next" "\$state_file"; fi
      echo "\$current" ;;
    start-instances) echo running > "\$state_file" ;;
    stop-instances) echo stopped > "\$state_file" ;;
    *) exit 2 ;;
  esac
  exit 0
fi
[[ "\$1" == s3 ]] || exit 2
shift
case "\$1" in
  cp)
    shift
    recursive=false
    while [[ "\$1" == --* ]]; do [[ "\$1" == --recursive ]] && recursive=true; shift; done
    src=\$1 dst=\$2
    [[ "\$src" == s3://* ]] && src=\$(local_path "\$src")
    [[ "\$dst" == s3://* ]] && dst=\$(local_path "\$dst")
    if [[ "\$dst" == - ]]; then cat "\$src"; exit 0; fi
    if \$recursive; then
      [[ -d "\$src" ]] || exit 1
      mkdir -p "\$dst"; cp -R "\$src"/. "\$dst"/
      exit 0
    fi
    [[ -f "\$src" ]] || exit 1
    mkdir -p "\$(dirname "\$dst")"; cp "\$src" "\$dst"
    ;;
  ls)
    target=\$(local_path "\$2")
    [[ -e "\$target" ]] || exit 1
    for entry in "\$target"/*; do
      [[ -e "\$entry" ]] || continue
      if [[ -d "\$entry" ]]; then echo "                           PRE \$(basename "\$entry")/"; else echo "2026-11-17 00:00:00 1 \$(basename "\$entry")"; fi
    done
    ;;
  *) exit 2 ;;
esac
EOF

# export는 학습 줄·스키마·보고서를, parity 시험은 호출만 남긴다.
cat > "$work/bin/fake-gradle" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "gradle flyway=${SPRING_FLYWAY_ENABLED:-} $*" >> "$CALLS"
for arg in "$@"; do
  case "$arg" in
    -Devaluation.export.path=*)
      out=${arg#*=}
      echo '{"qid":1}' > "$out"
      echo '{"feature_names":["a"]}' > "$(dirname "$out")/feature_schema.json"
      echo "# export report" > ../tasks/eval-training-set.md ;;
  esac
done
EOF

# uv run python -m <모듈> ... 에서 모듈마다 산출물을 만든다. 비교 종료 코드는 COMPARE_CODE로 정한다.
cat > "$work/bin/fake-uv" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "uv $*" >> "$CALLS"
module=$4
shift 4
value_of() { local key=$1; shift; while (($#)); do [[ "$1" == "$key" ]] && { echo "$2"; return; }; shift; done; }
case "$module" in
  dfgg_ltr.train) out=$(value_of --out "$@"); echo '{"trees":[2],"objective":"lambdarank"}' > "$out" ;;
  dfgg_ltr.propensity) dir=$(value_of --out-dir "$@"); mkdir -p "$dir"; echo '{}' > "$dir/propensity-game-report.json" ;;
  dfgg_ltr.export_parity_sample) out=$(value_of --out "$@"); echo '{}' > "$out" ;;
  dfgg_ltr.compare_models)
    out=$(value_of --out "$@"); echo "{\"exit_code\": ${COMPARE_CODE:-0}}" > "$out"
    exit "${COMPARE_CODE:-0}" ;;
  *) exit 9 ;;
esac
EOF
chmod +x "$work/bin/aws" "$work/bin/fake-gradle" "$work/bin/fake-uv"

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
fails() { ! "$@" > /dev/null 2>&1; }
logged() { grep -qF -- "$1" "$calls"; }

# 현재 운영 묶음: make-model-bundle.sh로 만든 진짜 형식
src="$work/src"
mkdir -p "$src/component"
echo '{"trees":[1],"objective":"lambdarank"}' > "$src/model.json"
echo '{"model":"component"}' > "$src/component/model.json"
echo '{"lookup":"policy"}' > "$src/component/component-policy-lookup.json"
echo '{"lookup":"situational"}' > "$src/component/situational-component-lookup.json"
MODEL_S3_PREFIX=$MODEL_S3_PREFIX "$pipeline/make-model-bundle.sh" --name ltr-261020 --core "$src/model.json" \
  --component "$src/component" --out "$work/seed" --upload > /dev/null
echo '{"name": "ltr-261020"}' > "$bucket/dfgg/ltr/current.json"
today="ltr-$(TZ=Asia/Seoul date +%y%m%d)"
ltr="$bucket/dfgg/ltr"

run() { "$pipeline/retrain.sh" --work "$work/run-$1" "${@:2}"; }

# 1. 통과
export GITHUB_OUTPUT="$work/github-output"
: > "$calls"
result=$(COMPARE_CODE=0 run pass --queries 5000 | tail -1)
check "통과하면 오늘 이름으로 올린다" test -f "$ltr/$today/model.json"
check "올린 Core 모델은 새로 학습한 모델이다" grep -q '"trees":\[2\]' "$ltr/$today/model.json"
check "Component 세 파일은 현재 묶음에서 그대로 옮긴다" cmp -s "$ltr/$today/component/component-policy-lookup.json" "$src/component/component-policy-lookup.json"
check "올린 묶음의 해시가 맞다" bash -c "cd '$ltr/$today' && sha256sum --quiet -c SHA256SUMS"
check "비교 결과와 export 보고서를 report/에 남긴다" test -f "$ltr/$today/report/compare.json" -a -f "$ltr/$today/report/export.md"
check "보고서는 해시 목록에 넣지 않는다" fails grep -q report/ "$ltr/$today/SHA256SUMS"
check "결과 JSON에 통과와 이름이 있다" test "$(jq -r '.verdict + " " + .name' "$result")" = "passed $today"
check "GITHUB_OUTPUT에 이름·판정을 적는다" grep -qx "verdict=passed" "$GITHUB_OUTPUT"
check "current.json은 바꾸지 않는다" test "$(jq -r .name "$ltr/current.json")" = ltr-261020
check "export는 통계를 다시 집계하지 않는다" logged "-Devaluation.aggregate=false"
check "export는 Flyway를 끄고 돈다" logged "gradle flyway=false evaluationTest --tests *TrainingSetExportTest"
check "query 수를 export에 넘긴다" logged "-Devaluation.queries=5000"
check "후보는 game split·기준 계약으로 학습한다" logged "--split game --baseline"
check "비교는 현재 운영 모델과 한다" logged "--current $work/run-pass/current/model.json"
check "Java parity는 기준 계약으로 확인한다" logged "-Devaluation.core.contract=baseline"
unset GITHUB_OUTPUT

# 2. 탈락·표본 부족은 -rejected로, 이름이 겹치지 않게 번호를 붙인다
COMPARE_CODE=1 run reject > /dev/null
check "기준 미달이면 -rejected로 올리고 번호를 붙인다" test -f "$ltr/$today-2-rejected/model.json"
check "탈락 모델은 통과 이름으로 올리지 않는다" test ! -e "$ltr/$today-2"
COMPARE_CODE=2 run insufficient > /dev/null
check "표본 부족도 -rejected로 올린다" test -f "$ltr/$today-3-rejected/model.json"
check "표본 부족 결과를 적는다" test "$(jq -r .verdict "$work/run-insufficient/result.json")" = insufficient

# 2-1. current.json이 없으면 이름 날짜가 가장 최근인 통과 묶음을 기준으로 삼고, 게임 상황 모델도 옮겨 담는다
echo '{"model":"game-situation"}' > "$src/game-situation.json"
MODEL_S3_PREFIX=$MODEL_S3_PREFIX "$pipeline/make-model-bundle.sh" --name ltr-991231 --core "$src/model.json" \
  --component "$src/component" --game-situation "$src/game-situation.json" --out "$work/seed" --upload > /dev/null
check "묶음에 게임 상황 모델을 담는다" grep -q " game-situation/model.json$" "$ltr/ltr-991231/SHA256SUMS"
mkdir -p "$ltr/ltr-999999-rejected"
rm "$ltr/current.json"
COMPARE_CODE=0 run latest > /dev/null
check "current.json이 없으면 가장 최근 통과 묶음과 비교한다" test "$(jq -r .current "$work/run-latest/result.json")" = ltr-991231
check "현재 묶음의 게임 상황 모델을 그대로 담는다" cmp -s "$ltr/$today-4/game-situation/model.json" "$src/game-situation.json"
check "옮겨 담은 게임 상황 모델도 해시 목록에 있다" bash -c "cd '$ltr/$today-4' && sha256sum --quiet -c SHA256SUMS && grep -q game-situation SHA256SUMS"
echo '{"name": "ltr-261020"}' > "$ltr/current.json"

# 3. 실패
check "비교가 오류로 끝나면 실패하고" fails env COMPARE_CODE=3 "$pipeline/retrain.sh" --work "$work/run-error"
check "아무것도 올리지 않는다" test ! -e "$ltr/$today-5" -a ! -e "$ltr/$today-5-rejected"
check "DB 접속 정보가 없으면 실패한다" fails env -u EVALUATION_DB_PASSWORD "$pipeline/retrain.sh" --work "$work/run-nodb"
mkdir -p "$work/run-dirty" && touch "$work/run-dirty/old"
check "작업 폴더가 비어 있지 않으면 실패한다" fails run dirty
check "query 수가 숫자가 아니면 실패한다" fails run badq --queries 3k
echo '{"model":"tampered"}' > "$ltr/ltr-261020/component/model.json"
check "현재 묶음의 해시가 다르면 실패한다" fails run tampered

# 4. training-instance.sh
export ML_INSTANCE_ID=i-0123456789abcdef0 POLL_SECONDS=0 WAIT_SECONDS=5
instance() { "$pipeline/training-instance.sh" "$@"; }
echo stopped > "$work/ec2-state"
check "start는 켜고 running까지 기다린다" test "$(instance start)" = "$ML_INSTANCE_ID running"
: > "$calls"
check "이미 켜져 있으면 다시 켜지 않는다" instance start > /dev/null
check "start-instances를 부르지 않는다" fails logged "start-instances"
check "stop은 끄고 stopped까지 기다린다" test "$(instance stop)" = "$ML_INSTANCE_ID stopped"
printf 'stopping\nstopping\nstopped\n' > "$work/ec2-state"
check "끄는 중이면 다 꺼진 뒤 켠다" test "$(instance start)" = "$ML_INSTANCE_ID running"
printf 'pending\nrunning\n' > "$work/ec2-state"
check "켜는 중이면 다 켜진 뒤 끈다" test "$(instance stop)" = "$ML_INSTANCE_ID stopped"
check "인스턴스 ID가 없으면 실패한다" fails env -u ML_INSTANCE_ID "$pipeline/training-instance.sh" status
check "다른 형식의 ID는 받지 않는다" fails env ML_INSTANCE_ID='i-1 --instance-ids i-2' "$pipeline/training-instance.sh" status
echo pending > "$work/ec2-state"
check "제한 시간 안에 상태가 안 바뀌면 실패한다" fails env WAIT_SECONDS=0 "$pipeline/training-instance.sh" stop

# 5. idle-stop.sh
export STATE_FILE="$work/idle-since" UPTIME_FILE="$work/uptime" SHUTDOWN_CMD="touch $work/shutdown"
idle() { rm -f "$work/shutdown"; "$ops/ml/idle-stop.sh" > /dev/null; }
runner_busy() { printf '#!/usr/bin/env bash\nexit %s\n' "$1" > "$work/bin/pgrep"; chmod +x "$work/bin/pgrep"; }
runner_busy 1
echo "600.0 1.0" > "$UPTIME_FILE"
idle
check "켜진 지 얼마 안 됐으면 끄지 않는다" test ! -e "$work/shutdown" -a ! -e "$STATE_FILE"
echo "3600.0 1.0" > "$UPTIME_FILE"
idle
check "작업이 없으면 유휴 시작 시각을 남기고" test -s "$STATE_FILE" -a ! -e "$work/shutdown"
echo $(($(date +%s) - 31 * 60)) > "$STATE_FILE"
idle
check "30분 넘게 작업이 없으면 끈다" test -e "$work/shutdown"
runner_busy 0
echo $(($(date +%s) - 31 * 60)) > "$STATE_FILE"
idle
check "작업 중이면 끄지 않고 유휴 기록을 지운다" test ! -e "$work/shutdown" -a ! -e "$STATE_FILE"
echo "21700.0 1.0" > "$UPTIME_FILE"
idle
check "작업 중이어도 6시간이 지나면 끈다" test -e "$work/shutdown"

echo
echo "passed $passed, failed $failed"
((failed == 0))
