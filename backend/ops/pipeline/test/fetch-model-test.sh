#!/usr/bin/env bash
# fetch-model.sh·make-model-bundle.sh를 S3 없이 시험한다. 가짜 aws가 로컬 폴더를 버킷처럼 쓴다.
#
#   사용법: backend/ops/pipeline/test/fetch-model-test.sh

set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
pipeline=$(dirname "$here")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

bucket="$work/bucket"
export MODEL_S3_PREFIX="s3://test-bucket/dfgg/ltr"
export MODEL_ROOT="$work/models"
export SLOT_ROOT="$work/slots"
export PATH="$work/bin:$PATH"
mkdir -p "$bucket/dfgg/ltr" "$work/bin" "$SLOT_ROOT"

# s3://test-bucket/<key>를 $bucket/<key>로 바꿔 cp·ls만 흉내 낸다.
cat > "$work/bin/aws" <<EOF
#!/usr/bin/env bash
set -euo pipefail
local_path() { echo "$bucket/\${1#s3://test-bucket/}"; }
[[ "\$1" == s3 ]] || exit 2
shift
case "\$1" in
  cp)
    shift
    [[ "\$1" == --only-show-errors ]] && shift
    recursive=false
    if [[ "\$1" == --recursive ]]; then recursive=true; shift; fi
    [[ "\$1" == --only-show-errors ]] && shift
    src=\$1 dst=\$2
    [[ "\$src" == s3://* ]] && src=\$(local_path "\$src")
    [[ "\$dst" == s3://* ]] && dst=\$(local_path "\$dst")
    if [[ "\$src" == - ]]; then mkdir -p "\$(dirname "\$dst")"; cat > "\$dst"; exit 0; fi
    if [[ "\$dst" == - ]]; then cat "\$src"; exit 0; fi
    if \$recursive; then
      [[ -d "\$src" ]] || exit 1
      mkdir -p "\$dst"; cp -R "\$src"/. "\$dst"/
      exit 0
    fi
    [[ -f "\$src" ]] || { echo "404 Not Found" >&2; exit 1; }
    mkdir -p "\$(dirname "\$dst")"; cp "\$src" "\$dst"
    ;;
  ls)
    target=\$(local_path "\$2")
    [[ -e "\$target" ]] || exit 1
    ls "\$target"
    ;;
  *) exit 2 ;;
esac
EOF
chmod +x "$work/bin/aws"

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

# 묶음 재료: Core 모델 하나와 Component 폴더 하나
core="$work/src/core-model.json"
component="$work/src/component"
mkdir -p "$component"
echo '{"trees":[1],"objective":"lambdarank"}' > "$core"
echo '{"model":"component"}' > "$component/model.json"
echo '{"lookup":"policy"}' > "$component/component-policy-lookup.json"
echo '{"lookup":"situational"}' > "$component/situational-component-lookup.json"
echo '{"ignored":true}' > "$component/evaluation.json"

make_bundle() {
  "$pipeline/make-model-bundle.sh" --name "$1" --core "$core" --component "$component" \
    --out "$work/out" --upload > /dev/null
}
fetch() { "$pipeline/fetch-model.sh" "$@"; }
point_to() { echo "{\"name\": \"$1\"}" > "$bucket/dfgg/ltr/current.json"; }

# 1. 정상 묶음 설치
check "묶음을 만들어 올린다" make_bundle ltr-261101
check "올린 묶음에 필수 파일과 SHA256SUMS가 있다" test -f "$bucket/dfgg/ltr/ltr-261101/component/situational-component-lookup.json" -a -f "$bucket/dfgg/ltr/ltr-261101/SHA256SUMS"
check "Component 폴더의 다른 파일은 담지 않는다" test ! -e "$bucket/dfgg/ltr/ltr-261101/component/evaluation.json"
check "같은 이름으로 다시 올리지 않는다" fails make_bundle ltr-261101
point_to ltr-261101
installed=$(fetch | tail -1)
check "current.json이 가리키는 묶음을 설치하고 경로를 출력한다" test "$installed" = "$MODEL_ROOT/ltr-261101"
check "설치한 묶음의 해시가 맞다" bash -c "cd '$installed' && sha256sum --quiet -c SHA256SUMS"
check "다시 실행해도 같은 경로를 출력한다" test "$(fetch | tail -1)" = "$installed"
check "--name으로 묶음을 고를 수 있다" test "$(fetch --name ltr-261101 | tail -1)" = "$installed"

# 2. 손상·누락·잘못된 이름
cp -R "$bucket/dfgg/ltr/ltr-261101" "$bucket/dfgg/ltr/ltr-261102"
# JSON으로는 멀쩡하고 해시만 다르게 바꾼다.
echo '{"model":"tampered"}' > "$bucket/dfgg/ltr/ltr-261102/component/model.json"
check "해시가 다르면 설치하지 않는다" fails fetch --name ltr-261102
check "실패한 묶음은 남기지 않는다" test ! -e "$MODEL_ROOT/ltr-261102"

cp -R "$bucket/dfgg/ltr/ltr-261101" "$bucket/dfgg/ltr/ltr-261103"
rm "$bucket/dfgg/ltr/ltr-261103/component/component-policy-lookup.json"
check "필수 파일이 없으면 설치하지 않는다" fails fetch --name ltr-261103

cp -R "$bucket/dfgg/ltr/ltr-261101" "$bucket/dfgg/ltr/ltr-261104"
grep -v situational "$bucket/dfgg/ltr/ltr-261104/SHA256SUMS" > "$work/sums"
mv "$work/sums" "$bucket/dfgg/ltr/ltr-261104/SHA256SUMS"
check "필수 파일이 SHA256SUMS에 없으면 설치하지 않는다" fails fetch --name ltr-261104

cp -R "$bucket/dfgg/ltr/ltr-261101" "$bucket/dfgg/ltr/ltr-261105-rejected"
check "탈락 묶음 이름은 받지 않는다" fails fetch --name ltr-261105-rejected
check "경로를 벗어나는 이름은 받지 않는다" fails fetch --name ../ltr-261101
rm "$bucket/dfgg/ltr/current.json"
check "current.json이 없으면 실패한다" fails fetch
point_to ltr-261101

# 3. dry-run은 아무것도 쓰지 않는다
make_bundle ltr-261106
before=$(ls "$MODEL_ROOT")
check "dry-run은 성공한다" bash -c "'$pipeline/fetch-model.sh' --name ltr-261106 --dry-run > /dev/null"
check "dry-run은 설치하지 않는다" test "$(ls "$MODEL_ROOT")" = "$before"
fetch --name ltr-261106 > /dev/null
sleep 1

# 4. 최근 3개만 남기되 사용 중인 묶음은 지우지 않는다
mkdir -p "$SLOT_ROOT/blue"
ln -s "$MODEL_ROOT/ltr-261101" "$SLOT_ROOT/blue/model"
mkdir -p "$MODEL_ROOT/component-ltr-16.19-261020"
for name in ltr-261107 ltr-261108 ltr-261109; do
  make_bundle "$name"
  fetch --name "$name" > /dev/null
  sleep 1
done
check "사용 중인 묶음은 오래돼도 남는다" test -d "$MODEL_ROOT/ltr-261101"
check "가장 오래된 미사용 묶음은 지운다" test ! -e "$MODEL_ROOT/ltr-261106"
check "최근 3개는 남는다" test -d "$MODEL_ROOT/ltr-261107" -a -d "$MODEL_ROOT/ltr-261108" -a -d "$MODEL_ROOT/ltr-261109"
check "묶음 규칙이 아닌 폴더는 건드리지 않는다" test -d "$MODEL_ROOT/component-ltr-16.19-261020"

echo "passed=$passed failed=$failed"
[[ "$failed" == 0 ]]
