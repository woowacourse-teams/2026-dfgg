#!/usr/bin/env bash
# EC2의 실제 Core 모델·추천 통계와 Component 모델을 연결하는 읽기 전용 검증.
# 학습 데이터를 다시 만드는 명령이 아니다. 결과: connection-report.json.
set -euo pipefail

if [[ "${1:-}" == "--help" ]]; then
  cat <<'HELP'
EC2에서 저장소의 backend/dfgg 디렉터리를 기준으로 실행합니다.

export EVALUATION_DB_URL='jdbc:postgresql://127.0.0.1:5432/사용할DB'
export EVALUATION_DB_USERNAME='DB사용자'
export EVALUATION_DB_PASSWORD='DB비밀번호'

CORE_MODEL_PATH=/opt/dfgg/models/core/model.json \
COMPONENT_MODEL_PATH=/opt/dfgg/models/component/model.json \
ITEM_CATALOG_PATH=/opt/dfgg/catalog/16.18.1.json \
MATCH_ID=KR_8375974758 PARTICIPANT_ID=9 PURCHASE_ORDER=34 \
bash scripts/component-connection.sh

세 파일 경로는 예시입니다. 실제 EC2 파일의 절대 경로로 바꾸세요.
지정한 경기의 Core 정규화 참가자 10명·아이템 이벤트·아이템 메타데이터와
기존 Core 추천에 필요한 통계 테이블이 같은 DB에 있어야 합니다.
PURCHASE_ORDER는 단일 구매가 아닌 묶음 첫 구매의 순번입니다.
CORE_TOP_K=5, COMPONENT_OUTPUT_DIR=build/component-connection은 변경할 수 있습니다.

보고서의 coreTopK / targetRecommendations를 확인하세요.
예산은 해당 구매 직전 Gold Range의 하한입니다. Live currentGold가 아닙니다.
각 목표별 46개 Feature로 독립 순위를 계산합니다. 공통 후보도 목표별로 유지합니다.
기존 사후 Core 조건의 Component 모델을 사용한 연결 확인이며,
Core 모델·통계의 학습 경기 누수나 운영 성능을 검증한 결과는 아닙니다.
HELP
  exit 0
fi

: "${EVALUATION_DB_URL:?EC2에서 사용할 DB URL을 지정하세요}"
: "${EVALUATION_DB_USERNAME:?DB 사용자를 지정하세요}"
: "${EVALUATION_DB_PASSWORD:?DB 비밀번호를 환경 변수로 지정하세요}"
: "${CORE_MODEL_PATH:?실제로 학습한 Core model.json의 절대 경로를 지정하세요}"
: "${COMPONENT_MODEL_PATH:?Component model.json의 절대 경로를 지정하세요}"
: "${ITEM_CATALOG_PATH:?해당 패치 카탈로그 JSON의 절대 경로를 지정하세요}"
: "${MATCH_ID:?확인할 matchId를 지정하세요}"
: "${PARTICIPANT_ID:?참가자 번호 1~10을 지정하세요}"
: "${PURCHASE_ORDER:?확인할 구매 묶음의 시작 purchaseOrder를 지정하세요}"

for file in "$CORE_MODEL_PATH" "$COMPONENT_MODEL_PATH" "$ITEM_CATALOG_PATH"; do
  if [[ "$file" != /* || ! -r "$file" ]]; then
    echo "읽을 수 있는 파일의 절대 경로가 필요합니다: $file" >&2
    exit 2
  fi
done

project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$project_root"
./gradlew evaluationTest --tests '*ComponentConnectionEvaluationTest.connect' \
  "-Drecommendation.ltr.model-path=file:$CORE_MODEL_PATH" \
  "-Drecommendation.component-ltr.model-path=file:$COMPONENT_MODEL_PATH" \
  "-Devaluation.component.catalog=$ITEM_CATALOG_PATH" \
  "-Devaluation.component.match-id=$MATCH_ID" \
  "-Devaluation.component.participant-id=$PARTICIPANT_ID" \
  "-Devaluation.component.purchase-order=$PURCHASE_ORDER" \
  "-Devaluation.component.core-top-k=${CORE_TOP_K:-5}" \
  "-Devaluation.component.output=${COMPONENT_OUTPUT_DIR:-build/component-connection}"
