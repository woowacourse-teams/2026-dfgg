# Component LTR Target Item 구현 결과 (2026-10-07)

설계 기준은 /Users/tuise/Downloads/COMPONENT_LTR_TARGET_ITEM_DESIGN.md다. 구현과 Dataset/Model 재생성, 학습·검증을 수행했다. **목표 관계 표현은 해결했지만 성능 향상은 확인되지 않아 모델은 deployable=false로 유지한다.**

## 1. 기존 구조에서 발견한 문제

- 미래 목표를 CORE 구매만으로 골라 BOOTS 목표가 없었다. 스냅샷 및 묶음의 COMPONENT/CORE 필터도 신발을 제외했다.
- 동일 상태·롱소드 후보에서 칠흑의 양날 도끼와 죽음의 무도가 같은 41개 Feature로 표현됐다.
- 기존 연결 smoke는 목표 간 후보를 합쳐 중복 제거하고 전역 정렬했다. 리플레이 쪽은 이미 목표별 순위였다.
- 서포터 최종형 5종은 실제 16.18.1 카탈로그에서 CORE로 분류된다. 일괄 OTHER 제외가 실제 원인은 아니다. 구매 없는 자동 승급은 스냅샷에 없고, 0골드 선택은 기존 item_cost>0 정책 때문에 COST_UNKNOWN이다. 전구체 미보유를 총가격 상점 구매로 잘못 해석할 위험도 있었다.
- Core 단계별 분리 학습은 기존에도 없었다. 이 정책을 유지했다.

## 2. 실제 변경한 설계

- TargetItemProgress가 기존 PurchaseRecipeMatcher의 수량 배정과 후보.inventoryAfter로 목표 진행 상태를 계산한다. Inventory Replay/후보 전이의 별도 구현은 없다.
- ComponentTargetPolicy가 일반 Core/2티어 신발/서포터 특수 획득을 판단하고, 누락·구매 불가 획득 경로를 명시적으로 거절한다.
- ComponentRecommendationService는 목표별 후보·점수·rankWithinTarget/status/reason을 반환한다. 공통 후보는 각 분기에 남고 목표 간 점수를 비교하지 않는다.
- CoreComponentRecommendationService가 기존 NextItemRecommendationService의 Top-5와 명시적인 현재 Inventory/예산을 연결한다. Core 순위를 보존하고 실패 목표를 6위로 교체하지 않는다.
- 실제 관측한 하나의 미래 목표만 학습 Query로 생성한다. 묶음 수량 완전 일치만 Label 1, 다른 유효 후보 0, 미확정 null/제외. 실제 의도/좋은 구매의 증명이라는 의미를 추가하지 않았다.
- 기존 10초/30초 묶음 경계, undo/판매/묶음 내 재조합 제외, item_cost, 슬롯, 퀘스트 슬롯, Gold Range 하한 정책은 유지했다.

## 3. 수정한 파일 목록

아래는 이번 작업에서 변경/추가한 파일이다. 작업 전부터 존재한 다른 수정과 실험 산출물은 보존했다.

### Java 도메인·학습·추론

- [src/main/java/dfgg/domain/match/TargetItemProgress.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/domain/match/TargetItemProgress.java)
- [src/main/java/dfgg/domain/match/ComponentTargetPolicy.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/domain/match/ComponentTargetPolicy.java)
- [src/main/java/dfgg/domain/match/BuildPathResolver.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/domain/match/BuildPathResolver.java)
- [src/main/java/dfgg/domain/match/ItemPurchaseType.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/domain/match/ItemPurchaseType.java)
- [src/main/java/dfgg/domain/match/PurchaseItemCatalog.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/domain/match/PurchaseItemCatalog.java)
- [src/main/java/dfgg/domain/match/PurchaseRecipeMatcher.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/domain/match/PurchaseRecipeMatcher.java)
- [src/main/java/dfgg/application/match/ComponentTrainingDataService.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/application/match/ComponentTrainingDataService.java)
- [src/main/java/dfgg/application/recommend/component/ComponentCandidateFeatures.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/application/recommend/component/ComponentCandidateFeatures.java)
- [src/main/java/dfgg/application/recommend/component/ComponentRecommendationService.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/application/recommend/component/ComponentRecommendationService.java)
- [src/main/java/dfgg/application/recommend/component/CoreComponentRecommendationService.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/application/recommend/component/CoreComponentRecommendationService.java)
- [src/main/java/dfgg/infrastructure/config/ComponentLtrConfiguration.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/main/java/dfgg/infrastructure/config/ComponentLtrConfiguration.java)

### Java 테스트·카탈로그 표본

- [src/test/java/dfgg/domain/match/TargetItemProgressTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/domain/match/TargetItemProgressTest.java)
- [src/test/java/dfgg/application/match/ComponentTrainingTargetTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/application/match/ComponentTrainingTargetTest.java)
- [src/test/java/dfgg/application/match/ParticipantItemPurchaseBundleTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/application/match/ParticipantItemPurchaseBundleTest.java)
- [src/test/java/dfgg/application/match/ComponentDatasetExportTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/application/match/ComponentDatasetExportTest.java)
- [src/test/java/dfgg/application/recommend/component/ComponentCandidateFeaturesTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/application/recommend/component/ComponentCandidateFeaturesTest.java)
- [src/test/java/dfgg/application/recommend/component/ComponentTargetRelationshipTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/application/recommend/component/ComponentTargetRelationshipTest.java)
- [src/test/java/dfgg/application/recommend/component/ComponentModelContractTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/application/recommend/component/ComponentModelContractTest.java)
- [src/test/java/dfgg/application/recommend/component/CoreComponentRecommendationServiceTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/application/recommend/component/CoreComponentRecommendationServiceTest.java)
- [src/test/java/dfgg/application/recommend/v3/ranker/ComponentLightGbmParityTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/application/recommend/v3/ranker/ComponentLightGbmParityTest.java)
- [src/test/java/dfgg/evaluation/ComponentConnectionEvaluationTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/evaluation/ComponentConnectionEvaluationTest.java)
- [src/test/java/dfgg/evaluation/ComponentCoreBridgeContractTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/evaluation/ComponentCoreBridgeContractTest.java)
- [src/test/java/dfgg/evaluation/ComponentCoreBridgeReplayTest.java](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/java/dfgg/evaluation/ComponentCoreBridgeReplayTest.java)
- [src/test/resources/item/component-target-recipes-16.18.1.json](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/src/test/resources/item/component-target-recipes-16.18.1.json)

### Python 계약·학습·분석

- [../ml/component_ltr/feature_schema.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/ml/component_ltr/feature_schema.py)
- [../ml/component_ltr/dataset.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/ml/component_ltr/dataset.py)
- [../ml/component_ltr/train.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/ml/component_ltr/train.py)
- [../ml/component_ltr/evaluate.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/ml/component_ltr/evaluate.py)
- [../ml/component_ltr/analyze.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/ml/component_ltr/analyze.py)
- [../ml/component_ltr/compare.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/ml/component_ltr/compare.py)
- [../ml/tests/test_component_dataset.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/ml/tests/test_component_dataset.py)

### 실행·문서

- [scripts/component-connection.sh](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/scripts/component-connection.sh)
- [scripts/component-core-bridge-analyze.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/scripts/component-core-bridge-analyze.py)
- [scripts/run-component-target-experiments.py](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/scripts/run-component-target-experiments.py)
- [.gitignore](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/.gitignore)
- [implementation_investigation.md](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/implementation_investigation.md)
- [component_target_item_implementation_report.md](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/component_target_item_implementation_report.md)

## 4. 신규 Feature 5개의 정확한 계산 정의

목표 T, 구매 전 Inventory B, 기존 구매 후보가 가진 구매 후 Inventory A를 사용한다. 전체 레시피의 중복 수량/검증된 동등 변형 재료를 기존 Matcher 정책으로 대응하며, 미래 정답·시각·관측 비용을 입력하지 않는다.

| 신규 열 (기존 41개 뒤 순서) | 정의 |
|---|---|
| 41 `target_remaining_gold_after_purchase` | 목표 보유 시 0. 그 외 `T.totalPrice - materialsFor(T,A).totalPrice`. 무관한 보유품 가격을 빼지 않고 각 레시피 위치를 한 번만 채운다. 후보 지출을 다시 더하지 않는다. 지원되는 비음수 조합비 경로에서는 완성품 직접 조합 구매가 이 비용으로 가능하므로 추가 최소 비용이다. |
| 42 `target_remaining_recipe_depth` | **실제로 가능한 최소 조합 연산 수**. 기존 구매 정책에서 완성 목표를 직접 조합 구매할 수 있으므로 미완성 비말단 목표 1, 보유 목표 0. leaf 구매는 조합이 아니므로 0. 미충족 레시피 노드 수와는 다르다. 이 보수적 정의가 기존 완료 Feature와 정보 중복이라는 한계는 아래에 기록했다. |
| 43 `contains_direct_target_material` | 후보에서 새로 구매한 ID 중 `T.ingredients`의 직접 자식이 하나라도 있으면 1, 아니면 0. 목표 자체 구매와 기존 직접 재료 소비만 있는 경우는 0. |
| 44 `target_material_coverage_after_purchase` | `1 - 미충족 최하위 재료 수량 / 전체 목표의 최하위 재료 수량`. 중복 재료를 multiset으로 센다. 보유 중간품/검증된 동등 변형품은 그 레시피 가지를 닫는다. 목표 보유 시 1. |
| 45 `target_progress_gain` | `clamp(1-remainingGold(A,T)/T.totalPrice,0,1) - clamp(1-remainingGold(B,T)/T.totalPrice,0,1)`. 후보 총가격/능력치 증가를 그대로 사용하지 않는다. |

분모 0, 누락/순환/음수 조합비, 미보유 구매 불가 재료/미확인 획득 경로는 임의 0으로 채우지 않고 오류/미지원 상태로 제외한다. 조합 단계는 설계 문서의 권장 최소 실제 연산 의미를 택했다. 레시피 복잡도를 뜻하는 미충족 비말단 수를 조용히 대입하지 않았다.

실제 16.18.1 카탈로그·빈 Inventory·600초 TOP·롱소드 1개(350골드) 사례:

| 목표 | 잔여 골드 | 조합 연산 | 직접 재료 | 재료 충족률 | 진행도 증가 |
|---|---:|---:|---:|---:|---:|
| 칠흑의 양날 도끼 3071 | 2650 | 1 | 0 | 1/5 | 350/3000 |
| 죽음의 무도 6333 | 2950 | 1 | 0 | 1/7 | 350/3300 |

실제 패치 레시피에는 2022 재료와 중복 1029/1036 요구량이 포함된다. 간소화한 가상 레시피로 이 사례의 값을 정하지 않았다. 기존 41개 값은 동일하지만 신규 벡터는 다르다. 모델 점수가 반드시 달라야 한다는 불변식은 없다.

## 5. Target Item 범위 처리 방식

- 일반 Core: 관측된 CORE 구매 목표와 현재 패치 레시피를 사용한다.
- 2티어 신발: 직접 레시피에 하위 기본 신발(Boots leaf)이 있는 구매 가능한 목표. 기본 장화는 Candidate/보유 재료로 허용하고 독립 목표로 삼지 않는다. 3티어 특수 승급을 새 학습 목표로 넓히지 않았다.
- 서포터 최종형: 현재 패치에서 구매 불가 3867 전구체를 요구하는 경로를 특수 획득으로 판단한다. 보유 전구체가 복원되면 0골드 선택 추론이 가능하고, 없으면 UNSUPPORTED_TARGET_MECHANIC이다. 6621은 이 패치의 일반 Dawncore 구매로 처리한다.
- 자동 획득 기록에서 상점 구매/Label을 합성하지 않았다. 기존 양수 item_cost 정책을 유지하므로 0골드 최종형 선택은 학습 제외다.
- 카탈로그 누락은 UNSUPPORTED_CATALOG_ITEM, 다른 미지원 유형은 UNSUPPORTED_TARGET_TYPE, 계산 실패는 CALCULATION_FAILED 등으로 분기에 보존된다. 다른 분기의 결과를 제거하지 않는다.

Export 결과: 전체 9,175경기 / 1,114,551묶음. 학습 비교 그룹은 **73,742개 = 일반 Core 69,948 + 2티어 신발 3,794**다. 서포터 목표가 선택된 묶음은 1,590개지만 학습 비교 그룹은 0개다. 중복 제외 사유 기준 COST_UNKNOWN 1,123개, 미보유 전구체로 346개가 명시적으로 실패했고, 후보가 생긴 1개 묶음도 관측 정답이 없었다. 서포터 성능을 검증했다고 주장하지 않는다.

## 6. Dataset 계약 변경

- `component-bundle-validation-v5`, 모델 계열 `component-ltr-v5`, Feature 46개.
- 기존 41개의 이름·순서·계산을 유지했고 위 5개를 뒤에 추가했다. 기존 `completes_target_core` 열 이름도 호환 추적을 위해 유지했다.
- `targetItemId`, `targetSource=OBSERVED_FUTURE_TARGET_ITEM`. Java의 기존 targetCoreId() 접근자는 소스 호환을 위해 유지하되 JSON은 targetItemId를 내보낸다.
- Query 식별자에 목표 ID를 포함했다. `targetKind`, `completedCoreCount`는 평가 메타데이터이며 모델 Feature가 아니다. 단계 count는 과거 CORE 구매 중 현재 Inventory에 직접 남은 고유 ID 수이고 BOOTS/취소를 제외한다. 변형 아이템의 단계 정의는 기존 정책과 동일하다고 과장하지 않는다.
- 경기별 기존 SHA256 정렬 80/10/10 분할을 유지했다. 한 Query에 Label 1 하나, 후보 중복/유한값/Feature 순서/경기 중복/manifest 검증을 유지한다.
- 지문: `54d9de4694c4f106003206c2489c3ba8cdd0346aad3c8c375fed32a4682e9188`.
- 기존 v4 데이터와 41열 모델을 새 서빙 계약에서 거절한다. Baseline/Ablation은 실제 열을 제거해 별도 지문을 가진 실험 모델로 저장하므로 46열 서빙과 호환되지 않는다.

## 7. 테스트 결과

- Java 전체 **1,151개 통과**, 실패/오류/skip 0.
- Python 전체 **128개 통과**.
- 각 관계 Feature, 실제 패치 롱소드 반례, 직접/간접 재료, 보유품 소비, 반복 재료, 동등 변형, 완성, 2티어 신발 유무, Gold/슬롯 경계, 서포터 0비용 학습 제외/전구체 추론, 미지원 ID, 독립 Top-5, Label 하나, 모든 단계 동일 계약, 구모델 거절을 검사했다.
- Java ↔ 원본 LightGBM parity: **1,677벡터, 최대 절대 오차 0**, 경계/NaN 표본 포함. 실제 Core 모델 계약과 합성 Target 반례 probe도 통과했다.
- `git diff --check` 통과.

## 8. 재학습 결과

동일 v5 후보/Label/분할, seed=158, LambdaRank, learning_rate=0.05, num_leaves=15, min_data_in_leaf=50, 최대 1,200 rounds, validation early stopping=30. Train 58,980 / Validation 7,374 / Test 7,388 Query. 모든 Core 단계와 지원 목표를 하나의 모델에서 함께 학습한다. best_iteration=212.

새 46열 모델 Validation Hit@1=86.3303%, MRR=0.925923, NDCG@3=0.941552. Test 지표는 다음 표와 같다. 각 Feature 하나씩 제거한 Ablation도 완료했다.

| 모델 | 열 | 동일 v5 Test Query | Hit@1 | Hit@3 | MRR | NDCG@3 |
|---|---:|---:|---:|---:|---:|---:|
| 동일 데이터 41열 기준선 | 41 | 7,388 | 86.9924% | 99.2014% | 0.929538 | 0.944456 |
| 신규 전체 | 46 | 7,388 | 86.8165% | 99.2420% | 0.928706 | 0.944045 |
| 잔여 골드 제거 | 45 | 7,388 | 86.2615% | 99.1337% | 0.925606 | 0.941296 |
| 조합 단계 제거 | 45 | 7,388 | 86.8165% | 99.2420% | 0.928706 | 0.944045 |
| 직접 재료 제거 | 45 | 7,388 | 87.2767% | 99.2420% | 0.931115 | 0.945761 |
| 재료 충족률 제거 | 45 | 7,388 | 87.1007% | 99.2555% | 0.930169 | 0.945144 |
| 진행도 증가 제거 | 45 | 7,388 | 86.5999% | 99.2691% | 0.927512 | 0.943292 |

Target 유형/역할/골드/구매 단계 지표는 각 evaluation.json의 stratified_test/stratified_validation에 기록했다. 신규 Test 일반 Core 7,028개 Hit@1=86.7814%, 2티어 신발 360개 Hit@1=87.5%. 보유 Core 단계 0/1/2/3+ Test Query는 3,485/2,081/1,271/551개이며 각각 Hit@1=86.4562%/87.6021%/87.5688%/84.3920%다. 단계별 모델은 만들지 않았다.

## 9. 기존 모델 대비 평가 결과

같은 경기/구매 묶음/목표/후보 수량/Label/기존 41개 값을 가지는 **공통 Core Test 7,028 Query**에서 비교했다. 최신 기존 v4 role-slot 모델의 원본 데이터 manifest 및 Test 경기 소속과 일치함을 확인했다. 기존 Test 7,441개 전체 지표와 v5 Test 7,388개 전체 지표를 같은 분모인 것처럼 비교하지 않았다.

| 모델 | 공통 Query | Hit@1 | MRR | NDCG@3 |
|---|---:|---:|---:|---:|
| 기존 v4 role-slot 모델 | 7,028 | 86.8241% | 0.928513 | 0.943841 |
| 동일 v5 데이터로 재학습한 41열 | 7,028 | 86.9237% | 0.929047 | 0.943980 |
| 신규 전체 46열 | 7,028 | 86.7814% | 0.928386 | 0.943706 |

신규 관계 표현은 검증됐지만 전체 성능 우위는 확인되지 않았다. 직접 재료 제거 모델의 Test 점수가 더 높다는 사실만으로 최종 계약을 45열로 변경하거나 이 모델을 승격하지 않았다. 하이퍼파라미터/Feature 선택을 Test 결과에 맞춰 다시 최적화하지 않았다. 모든 모델은 실험 상태(deployable=false)다.

추가로 운영 통계 snapshot의 실제 Core → Component 리플레이를 실행했다. **169경기 / 428시점**, Top-5 2,140개 독립 분기다. 관측 목표 Top-5 포함 384/428=89.7196%, 관측 목표 포함 조건부 Component Hit@1=312/384=81.25%, Core 누락을 포함한 연결 Hit@1=312/428=72.8972%. Oracle/export 및 같은 target 분기의 후보·Feature·점수·순위 불일치 0. 이 169경기 전체가 통계에 노출되어 **독립 Test 일반화 성능이 아니다**. 기존 진단의 163경기/415시점과 분모가 달라 직접 개선 수치로 사용하지 않는다.

## 10. 남은 한계와 Open Question

1. 남은 조합 단계는 문서 권장 실제 최소 연산으로 정의하면 0/1이고 기존 완료 Feature와 정보가 중복된다. 실제 gain=0, 제거 실험 결과 동일. 미충족 레시피 비말단 수/트리 깊이로 바꾸려면 별도 의미 합의와 계약 버전 변경이 필요하다.
2. 서포터 학습 비교 그룹 0개. 0골드 비용 정책, 자동 획득 이벤트/퀘스트 상태, 원천 Inventory 복원 정책을 별도 작업으로 확정하기 전에는 학습 커버리지를 채울 수 없다. 현재 전구체 보유 추론은 미관측 유형에 대한 모델 일반화이며 성능 검증이 아니다.
3. v5 candidate-comparable 분모에서 관측 후보 누락률 67.10%. 대부분 골드 하한 비용 확정 실패 등 기존 정책 영향이다. 이 비율을 단순 레시피 생성 오류율로 해석하지 않는다. 조건부 순위 점수만으로 전체 서비스 커버리지를 평가할 수 없다.
4. 학습 목표는 미래 실제 획득이며 실제 의도의 증거가 아니다. 실서비스의 Core 추천 목표 분포와 다르고, 통계/Core 이력에 노출되지 않은 완전 독립 연결 Test는 확보하지 않았다.
5. 기존 v3 HTTP 요청에는 실제 Inventory, 가용 골드, 게임 시간이 없다. 이 입력을 받는 애플리케이션 연결 서비스는 구현했지만 임의 추정/새 HTTP 계약/UI 표시는 추가하지 않았다. 실제 데이터 공급 및 API 계약은 별도 결정이 필요하다.
6. 패치 16.18.1에서 학습했다. 다른 패치는 해당 카탈로그/획득 메커니즘 검증과 재학습이 필요하다. 특별한 승급·캐릭터 구매 제한을 새로 추측하지 않았다.
7. 단계 메타데이터는 현 CORE 분류와 직접 보유 기준이다. 신발/자동 획득/변형을 포함하는 통일 Core 단계 의미는 별도 합의 대상이다. 새 단계 Feature는 없다.
8. 성능 향상/배포 적합성 미확인으로 기존 운영 모델을 자동 교체하지 않았다. 새 46열 모델 경로를 지정해야 새 계약을 사용할 수 있다.

## 산출물 및 재현

- [조사·계획](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/implementation_investigation.md)
- [v5 Dataset/제외 보고서](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-data/observed-target-v5-16.18/report.json)
- [Dataset 분할 manifest](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-data/observed-target-v5-16.18/manifest.json)
- [신규 모델](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/full/model.json)
- [Validation](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/full/validation-diagnostics.json)
- [Test/층화 평가](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/full/evaluation.json)
- [최신 v4 공통 Query/전체 Ablation 비교](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/comparison-role-slots.json)
- [이전 slot-fixed v4 비교(보조)](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/comparison.json)
- [목표별 커버리지와 제외 사유](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/target-coverage.json)
- [Java 점수 parity](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/full/java-parity-report.json)
- [실제 레시피 반례](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/target-probe/target_core_probe.json)
- [Core 연결 리플레이 진단](/Users/tuise/Desktop/woowa/2026-dfgg/backend/dfgg/build/component-target-v5-experiments/bridge-replay/summary.json)

```bash
# backend/dfgg: 로컬 DB 읽기 전용 Java export, 각 환경의 실제 DB/패치 카탈로그를 지정한다.
TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/dfgg_local TEST_DB_USERNAME=postgres TEST_DB_PASSWORD=postgres \
./gradlew evaluationTest --tests '*ComponentDatasetExportTest.export' \
-Devaluation.component.catalog=/absolute/path/catalog.json \
-Devaluation.component.output=/absolute/path/new-v5-dataset

python3 scripts/run-component-target-experiments.py \
--data-dir /absolute/path/new-v5-dataset --out-dir /absolute/path/new-v5-experiments --rounds 1200

# backend/ml
uv run python -m component_ltr.compare \
--data-dir /absolute/path/new-v5-dataset --experiments /absolute/path/new-v5-experiments \
--legacy-model /absolute/path/v4-model --legacy-test /absolute/path/v4-data/test.jsonl \
--out /absolute/path/new-v5-experiments/comparison.json
```

실험 runner는 기존 model.json이 있는 경로를 덮어쓰지 않는다. Python에서는 Feature를 재계산하지 않는다. 실제 생성 데이터/모델은 기존 저장소 관례대로 build 아래에 분리 저장했으므로 build clean 전 보존이 필요하다.
