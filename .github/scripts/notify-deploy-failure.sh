#!/usr/bin/env bash
# 배포 실패를 Slack으로 알린다.
#
#   ALERT_WEBHOOK_URL  Slack Incoming Webhook (GitHub Secret)
#   DEPLOY_ENV         dev | prod
#   DEPLOY_STAGE       deploy | verify
#   JOB_RESULT         failure | cancelled
#   RECOVERY           be-deploy.yml의 recovery 출력 (restored | new-slot-active | uncertain | 빈 값)
#   RUN_URL            실행 링크

set -euo pipefail

if [[ -z "${ALERT_WEBHOOK_URL:-}" ]]; then
  echo '::error::ALERT_WEBHOOK_URL secret is not set; deployment failure was not delivered'
  exit 1
fi

if [[ "$DEPLOY_STAGE" == verify ]]; then
  stage='체류 검증 (5분)'
  impact='새 버전이 dev에 떠 있지만 체류 검증을 통과하지 못했습니다. *prod 배포는 진행되지 않습니다* (승인해도 건너뜁니다).'
else
  stage='배포'
  case "${RECOVERY:-}" in
    restored)        impact='새 버전 전환에 실패해 *이전 버전으로 복구했습니다.* 서비스는 이전 버전으로 계속됩니다.' ;;
    new-slot-active) impact='새 버전은 서비스 중이지만 이전 슬롯 정리에 실패했습니다. *서버를 직접 확인하세요.*' ;;
    uncertain)       impact=':warning: *복구가 불확실합니다.* 두 앱 모두 멈추지 말고 즉시 서버를 확인하세요.' ;;
    *)               impact='앱 전환 전 단계(사전 점검·nginx 설정·빌드)에서 멈췄거나 결과를 알 수 없습니다. 전환 전이었다면 기존 버전이 그대로 서비스 중입니다. 로그를 확인하세요.' ;;
  esac
fi

result='실패'
[[ "$JOB_RESULT" == cancelled ]] && result='취소'

commit="$(git log -1 --format='%h %s' 2>/dev/null || echo "${GITHUB_SHA:0:7}")"

text=$(cat <<EOF
:rotating_light: *[배포 ${result}] ${DEPLOY_ENV}* — ${stage}
${impact}
커밋: \`${commit}\`
실행자: ${GITHUB_ACTOR}
<${RUN_URL}|실행 로그 열기>
EOF
)

jq -n --arg text "$text" '{text: $text}' \
  | curl -fsS --max-time 10 -H 'Content-Type: application/json' --data-binary @- "$ALERT_WEBHOOK_URL" > /dev/null
echo "Slack으로 알렸다: $DEPLOY_ENV $stage $result"
