#!/usr/bin/env bash
# 야간 파이프라인 결과를 Slack으로 알린다.
#
#   ALERT_WEBHOOK_URL  Slack Incoming Webhook (GitHub Secret)
#   LEVEL              failure | notice
#   MESSAGE            본문 (여러 줄 가능)
#   RUN_URL            실행 링크

set -euo pipefail

if [[ -z "${ALERT_WEBHOOK_URL:-}" ]]; then
  echo '::error::ALERT_WEBHOOK_URL secret is not set; pipeline notice was not delivered'
  exit 1
fi

icon=':information_source:'
title='야간 파이프라인 알림'
if [[ "$LEVEL" == failure ]]; then
  icon=':rotating_light:'
  title='야간 파이프라인 실패'
fi

text=$(printf '%s *[%s]*\n%s\n<%s|실행 로그 열기>' "$icon" "$title" "$MESSAGE" "$RUN_URL")
jq -n --arg text "$text" '{text: $text}' \
  | curl -fsS --max-time 10 -H 'Content-Type: application/json' --data-binary @- "$ALERT_WEBHOOK_URL" > /dev/null
echo "Slack으로 알렸다: $LEVEL"
