#!/usr/bin/env bash
# 학습 서버(ml) 인스턴스 하나만 켜고 끈다. 다른 인스턴스 ID는 받지 않는다.
#
#   사용법: training-instance.sh start|stop|status
#   start는 running이 될 때까지, stop은 stopped가 될 때까지 기다린다. 이미 그 상태면 바로 끝난다.
#
#   ML_INSTANCE_ID  ml 인스턴스 ID (필수)
#   AWS_REGION      기본값 ap-northeast-2
#   WAIT_SECONDS    최대 대기 (기본값 600)

set -Eeuo pipefail

region=${AWS_REGION:-ap-northeast-2}
wait_seconds=${WAIT_SECONDS:-600}
poll_seconds=${POLL_SECONDS:-10}
id=${ML_INSTANCE_ID:-}

usage() { echo "usage: $0 start|stop|status" >&2; exit 2; }
fail() { echo "error: $*" >&2; exit 1; }

[[ $# -eq 1 ]] || usage
[[ "$id" =~ ^i-[0-9a-f]{8,17}$ ]] || fail "ML_INSTANCE_ID is missing or invalid"

state() {
  aws ec2 describe-instances --region "$region" --instance-ids "$id" \
    --query 'Reservations[0].Instances[0].State.Name' --output text
}

wait_for() {
  local want=$1 deadline now
  deadline=$(($(date +%s) + wait_seconds))
  while true; do
    now=$(state)
    [[ "$now" == "$want" ]] && return 0
    (($(date +%s) < deadline)) || fail "$id is $now after ${wait_seconds}s (want $want)"
    sleep "$poll_seconds"
  done
}

case "$1" in
  status)
    state
    ;;
  start)
    current=$(state)
    # 끄는 중이면 다 꺼진 뒤에 켠다. stopping 상태에서는 start가 거부된다.
    if [[ "$current" == stopping ]]; then
      wait_for stopped
      current=stopped
    fi
    if [[ "$current" != running ]]; then
      aws ec2 start-instances --region "$region" --instance-ids "$id" > /dev/null
    fi
    wait_for running
    echo "$id running"
    ;;
  stop)
    current=$(state)
    # 켜는 중이면 다 켜진 뒤에 끈다. pending 상태에서는 stop이 거부된다.
    if [[ "$current" == pending ]]; then
      wait_for running
      current=running
    fi
    if [[ "$current" != stopped && "$current" != stopping ]]; then
      aws ec2 stop-instances --region "$region" --instance-ids "$id" > /dev/null
    fi
    wait_for stopped
    echo "$id stopped"
    ;;
  *) usage ;;
esac
