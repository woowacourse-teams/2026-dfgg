# Component Target Item 조사 및 구현 계획 (2026-10-07)

기준: /Users/tuise/Downloads/COMPONENT_LTR_TARGET_ITEM_DESIGN.md. 문서의 Feature 수식은 초안이며 조합 단계는 문서 권장 최소 실제 조합 연산수로 보수적으로 정의했다.

## 현재 구조와 불일치
- ComponentTrainingDataService: 한 참가자의 구매 묶음 시작 이후 첫 CORE 구매를 OBSERVED_FUTURE_CORE로 선택한다. 취소 제외, 목표 경계 검사, 실제 수량 완전 일치만 label=1. 미확정 query는 null. 모든 구매 단계를 함께 export한다.
- ParticipantItemPurchaseSnapshotService/ParticipantItemPurchaseBundle: isComponentRecommendationTarget가 COMPONENT/CORE만 허용하여 BOOTS 스냅샷과 묶음을 제외한다. 10초 이벤트 공백, 최대 30초, 판매/undo/묶음 내 재조합 제외.
- 서포터 최종형은 16.18.1에서 purchasable=true/from=[3867]이므로 CORE로 분류된다. 획득 구매 이벤트가 없는 자동 승급은 Component 구매 스냅샷에 없다. CoreItemPurchaseOrderCalculator는 파괴 앵커+최종 소지품으로 별도 순서 보정한다. 이를 Component label로 복제하면 가상 구매가 된다. 또한 PurchaseCostEstimator/NormalizedMatchParticipantItemEvent는 item_cost>0만 허용해 0골드 최종 선택 구매는 COST_UNKNOWN이다. 비용 정책 보존에 따라 학습 제외하되 전구체 보유 추론은 지원한다.
- BuildPathResolver: 패치 레시피와 PurchaseRecipeMatcher를 재사용, 보유 재료 수량 소비, totalPrice-소비재료가격, 슬롯 6개/퀘스트 슬롯, budget 하한, 묶음 내부 재조합 제외. 목표 자체를 총가격으로 직접 구매할 수 있다. 구매 불가 재료의 획득 조건은 검증하지 않아 서포터 전구체 미보유 상태를 일반 구매로 오인할 수 있다.
- ComponentCandidateFeatures: 41개 비용/수량/슬롯/완성/재료소비/능력치/시간/역할/보유 능력치. 목표 자체와 기본/중간 분류 외에는 목표 관계가 없다.
- ComponentDatasetExportTest: 경기 SHA256 정렬 80/10/10, READY+후보>=2만 분할 저장, 모든 제외 행 all.jsonl에 보존. v4 계약.
- ../ml/component_ltr: Java 벡터만 학습, 계약/유한값/중복 query/후보/정답 하나/경기 분할 검증. LambdaRank, validation early stopping, test 최종 평가. Query ID에 target이 없음.
- ComponentCoreBridgeReplayTest는 목표별 순위. ComponentConnectionEvaluationTest는 목표 간 후보 병합/전역 정렬하여 설계와 충돌. 운영 HTTP는 아직 Component 연결을 제공하지 않는다.
- ItemService/Core 후보 범위와 구매 로그의 CORE 분류는 서로 다르다. Core 단계 정의를 이번 변경에서 수정하지 않는다.

## 지원 정책
- 일반 CORE: 패치별 상점/레시피 지원 조건을 검사한다.
- 2티어 BOOTS: 레시피에 기본 신발(하위 Boots leaf)을 포함하고 구매 가능한 목표. 기본 신발 자체/3티어 특수 승급을 새로운 학습 목표로 확대하지 않는다.
- 서포터 최종형: 현재 보유 전구체로 실제 선택 구매가 가능한 경우만 지원. 미보유 구매 불가 leaf는 UNSUPPORTED_TARGET_MECHANIC. 자동 획득 label 합성 없음. 6621은 이 패치에서 Dawncore 일반 구매이므로 퀘스트 분류 목록을 그대로 사용하지 않는다.
- 누락 ID/레시피/능력치 및 패치 불일치는 명시적 오류/분기 상태. 다른 target으로 대체하지 않는다.

## 파일별 변경 책임
1. PurchaseRecipeMatcher/새 TargetItemProgress: 기존 수량 배정 결과로 잔여 골드/남은 조합/leaf coverage 계산. 별도 inventory replay 없음.
2. ComponentCandidateFeatures: 기존 41열 보존+5열. before/후보.inventoryAfter 사용.
3. ItemPurchaseType: BOOTS의 관측 구매 허용. ComponentTrainingDataService: 관측 target 범위, targetItemId/source/type, 계산불가 상태, v5.
4. 새 ComponentTargetPolicy/ComponentRecommendationService: 목표별 지원 검사/독립 후보 점수/상태 반환. CoreComponentRecommendationService가 기존 NextItemRecommendationService의 Core Top-5에 실제 현재 Inventory/예산을 연결한다. smoke/replay가 공통 계산 경로 사용.
5. ComponentDatasetExportTest: v5/source/target 분포와 메타데이터 기록.
6. ../ml/component_ltr/{feature_schema,dataset,train,analyze}: v5 46열 지문, target 포함 qid, source 검증, baseline/ablation과 분포별 평가 지원.
7. ComponentLtrConfiguration 및 Java/Python 테스트: 새 지문, 구모델 거절, 목표/후보 관계와 경계 테스트, parity.

## 관계 Feature 계약
- target_remaining_gold_after_purchase: 목표 보유 시 0, 아니면 target.totalPrice - PurchaseRecipeMatcher.materialsFor(target, after).totalPrice. 재료 배정은 기존 정책 그대로. 미래 골드/후보 지출 재가산 없음. 지원되지 않는 획득/음수 비용은 오류.
- target_remaining_recipe_depth: 실제 최소 조합 연산수. 기존 구매 정책은 완성품을 한 번에 구매할 수 있으므로 미완성 비말단 목표는 1, 보유 목표는 0, leaf 구매는 조합이 아니므로 0. 미충족 비말단 노드 수(레시피 복잡도)와 구분하며 낮은 정보량을 한계로 기록한다.
- contains_direct_target_material: 새 구매 묶음 ID 중 target.ingredients에 있는 ID가 하나라도 있으면 1. target 자체/기존 재료 소비만이면 0.
- target_material_coverage_after_purchase: 전체 목표 레시피 leaf 수량 대비 1 - remainingPath의 미충족 leaf 수량/전체 leaf 수량. 보유 중간품은 가지를 닫고 중복 배정하지 않음. 목표 보유면 1. leaf 목표 미보유면 0.
- target_progress_gain: clamp(1-remainingGold(after)/targetPrice)-clamp(1-remainingGold(before)/targetPrice). targetPrice<=0 계산 불가.

## 호환성과 검증 계획
v4 데이터를 v5로 이름만 바꾸지 않는다. Java로 재생성한다. 41열 baseline은 새 query/후보 동일 조건에서 5열을 비활성화한 비교 모델로 별도 저장, 기존 v4 모델도 공통 CORE test query에서 별도 측정한다. 각 Feature 제거 ablation. 목표 유형/역할/골드/기존 CORE 구매 단계로 평가하되 단계 Feature나 분리 모델 없음. 기존 모델 파일 덮어쓰기 없음.
로컬 DB와 Python LightGBM 이용 가능. Component 독립 경기 분할은 확인 가능하지만 Core/운영 통계 이력의 독립성은 기존 보고서처럼 미확인: 연결 결과를 배포 일반화 지표로 주장하지 않는다.

## 실측 지원 한계
서포터 목표가 선택된 묶음은 1,590개, 학습 비교 그룹은 0개다. COST_UNKNOWN은 중복 사유 기준 1,123개이며 미보유 전구체로 346개가 명시적으로 제외되었다. 일반 Core 69,948개와 2티어 신발 3,794개를 하나의 모델에서 학습/평가한다. 실제 최소 조합 단계는 0/1이고 기존 completes_target_core와 정보가 중복되어 gain=0이었다. 이 결과는 성능 향상을 의미하지 않는다.
