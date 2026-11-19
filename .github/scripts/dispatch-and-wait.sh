#!/usr/bin/env bash
# 워크플로를 수동 실행하고, 그 실행이 끝날 때까지 기다린다. 실행 번호를 마지막 줄에 출력한다.
#
#   사용법: dispatch-and-wait.sh <workflow 파일> <ref> [--no-wait] [-f key=value ...]
#   --no-wait  실행만 걸고 기다리지 않는다(승인이 필요한 prod 재기동처럼 사람이 이어받는 경우)
#   GH_TOKEN   actions: write 권한이 있는 토큰
#   GITHUB_REPOSITORY  owner/repo

set -Eeuo pipefail

workflow=${1:?workflow}
ref=${2:?ref}
shift 2
wait=true
fields=()
while (($#)); do
  case "$1" in
    --no-wait) wait=false; shift ;;
    -f) fields+=(-f "$2"); shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
gh workflow run "$workflow" --repo "$GITHUB_REPOSITORY" --ref "$ref" "${fields[@]}"

# 방금 건 실행을 찾는다. 같은 시각에 다른 수동 실행이 겹치면 가장 먼저 생긴 것을 고른다.
run=""
for _ in $(seq 1 30); do
  run=$(gh run list --repo "$GITHUB_REPOSITORY" --workflow "$workflow" --branch "$ref" --event workflow_dispatch \
    --limit 10 --json databaseId,createdAt \
    -q "[.[] | select(.createdAt >= \"$started\")] | sort_by(.createdAt) | .[0].databaseId // empty")
  [[ -n "$run" ]] && break
  sleep 5
done
[[ -n "$run" ]] || { echo "::error::cannot find the dispatched run of $workflow"; exit 1; }
echo "dispatched: ${GITHUB_SERVER_URL:-https://github.com}/$GITHUB_REPOSITORY/actions/runs/$run"

if $wait; then
  gh run watch "$run" --repo "$GITHUB_REPOSITORY" --exit-status --interval 30 > /dev/null
  echo "completed: $run"
fi
echo "$run"
