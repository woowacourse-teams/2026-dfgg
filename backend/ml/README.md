# dfgg LTR 학습 (LambdaMART)

v3 추천의 최종 랭커를 학습한다. **feature 계산은 하지 않는다** — Java가 내보낸 feature 벡터를
그대로 학습에 쓴다(train/serve skew 차단). 이 디렉터리는 **개발 시점 전용**이며 배포 경로에
포함되지 않는다.

## ⚠️ 선행조건 (macOS)

```bash
brew install libomp
```

없으면 휠은 설치되는데 import에서 실패한다:
`OSError: dlopen(...): Library not loaded: @rpath/libomp.dylib`

## 셋업

```bash
uv sync                    # Python 3.12 + lightgbm, numpy
uv sync --extra analysis   # 분석(T15)까지: pandas, scikit-learn, shap
```

`uv`가 없으면: `curl -LsSf https://astral.sh/uv/install.sh | sh`

## 사용

학습 데이터는 **Java가 만든다**. 먼저 `backend/dfgg`에서:

```bash
EVALUATION_DB_URL=jdbc:postgresql://127.0.0.1:5432/dfgg_test_backtest \
  ./gradlew evaluationTest --tests '*TrainingSetExportTest' -Devaluation.queries=30000
```

`../ml/data/`에 두 파일이 생긴다 — `train.jsonl`(학습 데이터)과
`feature_schema.json`(feature 이름·순서·지문). JSONL의 feature는 이름 없는 배열이라
스키마 파일이 없으면 Python이 각 칸이 무엇인지 알 수 없다.

그 다음 학습하고 모델을 내보낸다:

```bash
uv run python -m dfgg_ltr.train --split game --out ../dfgg/src/main/resources/ltr/model.json
uv run python -m dfgg_ltr.train --split patch    # 최신 패치 일반화 확인
```

테스트:

```bash
uv run pytest tests/ -q
```

## 디렉터리 경계

| 방향 | 실행 위치 | 경로 |
|---|---|---|
| 학습 데이터 export | `backend/dfgg` (Gradle) | → `../ml/data/train.jsonl` |
| 모델 export | `backend/ml` (uv) | → `../dfgg/src/main/resources/ltr/model.json` |

모델 JSON은 커밋한다. **재학습하는 사람만 Python이 필요하고**, 나머지 팀원과 CI/CD는
Java만으로 동작한다(`be-cd.yml`은 `working-directory: ./backend/dfgg`라 이 디렉터리를 보지 못한다).

## Java 추론과의 계약 (Task 12)

- **numeric feature만 사용.** `categorical_feature`, `linear_tree` 금지
- `dfgg_ltr/model_export.py`가 `dump_model()`의 중첩 트리를 **평탄 배열로 변환**해 내보낸다
- **자식 인덱스 규약**: 0 이상이면 분기 노드 인덱스, 음수면 잎이며 `-index - 1`이 잎 번호다.
  이 규약으로 순회하면 LightGBM 예측과 1e-9 이내로 일치한다(`tests/test_flatten_equivalence.py`)
- 범주형 분기가 섞이면 export 단계에서 **실패시킨다**
- `feature_names` 순서가 Java `FeatureName` enum과 정확히 일치해야 하며, 다르면 기동 실패
- NaN은 `default_left` 방향으로 라우팅 (`missing_type`도 함께 확인)
- parity 테스트: Python 예측과 Java 예측이 **1e-6 이내** 일치

## 검증된 환경

| 항목 | 값 |
|---|---|
| uv | 0.12.7 |
| Python | 3.12.14 (uv가 설치) |
| lightgbm | 4.7.0 |
| numpy | 2.5.2 |
| 플랫폼 | macOS arm64 |

`uv.lock`을 커밋한다 — LightGBM 버전이 달라지면 모델 dump 구조가 바뀌어 Java 로더가 깨진다.

## Component LTR 초기 학습

### 현재 Label 정책 (2026-10-07)

Label 1은 `observed purchase`, Label 0은 동일 Query의 `unobserved alternative`입니다. 관측되지 않은 대안을 잘못된 구매나 나쁜 구매로 판정하지 않습니다. 모델은 주어진 상태와 Target Item에서 수집된 플레이어의 관측 구매를 높은 순위에 배치하도록 학습합니다. Hit/MRR/NDCG는 관측 선택 재현 지표이며 객관적 최적성이나 승률을 측정하지 않습니다.

현재 0/1 relevance, Query마다 Label 1 하나, `itemId × quantity` 비교 계약을 유지합니다. 일반 `component_ltr.train`은 별도 Weight 없이 모든 Train Query를 학습합니다. COLLECTION_COHORT 티어 가중치는 실험 전용이며 일반 학습 정책에 적용하지 않습니다. soft/graded 및 성과 기반 Label은 보류합니다. 근거와 재검토 조건은 [Label 설계 결정](../dfgg/docs/component-ltr-label-design-decision.md)에 기록했습니다.

현재 Candidate는 Target Item의 조합 경로에서 생성합니다. Dataset export의 `out-of-target-path-purchases.jsonl.gz`는 경로 밖 구매 패턴을 조사하는 진단 파일이며 학습 입력이 아닙니다. 이후 완성품 등 미래 정보는 이 파일에만 기록합니다. 학습은 기존 train/validation/test.jsonl을 계속 사용합니다. 진단 출력과 재현 방법은 [Candidate 범위 설계](../dfgg/docs/component-ltr-candidate-scope-design.md)를 참고하세요.

기존 Python 3.12와 uv 환경을 공유합니다. Java가 만든 train/validation/test.jsonl을 같은 디렉터리에 놓습니다.

```bash
uv sync
uv run python -m component_ltr.train --data-dir ../dfgg/build/component-data/observed-core-16.18 --out-dir data/component-model
uv run pytest tests/test_component_dataset.py
```

component_ltr.dataset은 한 구매 묶음을 한 비교 그룹으로 읽습니다. 기존 Core Dataset 자료형을 재사용하고, 0/1 라벨·관측 구매 후보 하나·Feature 순서·중복 후보를 검사합니다. train은 학습, validation은 early stopping, test는 최종 평가에만 사용합니다. 경기 중복을 발견하면 중단합니다.

출력은 booster.txt(원본 모델), model.json(기존 Java 트리 형식), evaluation.json(모델과 비용 내림차순 기준선의 Hit@1/3·MRR·NDCG@3)입니다. 평탄 모델과 원본 점수의 일치도 100개 벡터에서 검사합니다. Java 점수 검증과 API 연결은 후속 작업입니다.

현재 OBSERVED_FUTURE_CORE는 실제 미래 Core를 목표로 한 조건부 실험입니다. 실제 골드 추정치와 미래 구매 시각은 학습 Feature로 읽지 않습니다. 비용·수량·슬롯의 5개 Feature만 사용합니다. 후보 누락률은 데이터 생성 보고서를 함께 확인해야 합니다.

macOS에서 libomp 오류가 나면 이 문서의 기존 brew install libomp 준비가 필요합니다. Linux EC2에서는 해당 환경의 OpenMP 런타임이 필요합니다.

### Component Python / Java 점수 검증

모델을 재학습하면 표본도 다시 생성합니다. 원본 booster 점수와 JSON 평탄 모델 점수가 일치하는지 Python에서 먼저 검사하고, 같은 표본을 Java에서 검사합니다.

```bash
# backend/ml
uv run python -m component_ltr.export_parity_sample --model-dir data/component-model --data ../dfgg/build/component-data/observed-core-16.18/test.jsonl --out ../dfgg/build/component-model/parity-sample.json

# backend/dfgg (아래 /absolute/path는 본인의 모델·표본 경로)
./gradlew evaluationTest --tests '*ComponentLightGbmParityTest' -Devaluation.component.model=/absolute/path/model.json -Devaluation.component.parity=/absolute/path/parity-sample.json
```

표본은 실데이터 1,000개, 임계값 경계, 전부 결측 및 0 벡터를 포함합니다. 모델 SHA-256과 Feature 이름·순서·스키마 지문을 검사하여 다른 모델의 표본을 사용하는 일을 막습니다. 허용 오차는 1e-9이며 결과는 표본 옆 java-parity-report.json에 기록됩니다. 기존 Core 운영 모델과 API는 교체하지 않습니다.

### Component Feature v2

새 데이터 계약은 component-bundle-validation-v2, Feature는 22개입니다. 기존 비용·수량·슬롯 5개 뒤에 Core 완성, 보유 재료 소비 수량/가격, 기본/중간 재료 구매 수량, 12개 기본 능력치 변화량을 붙입니다. 능력치는 구매 후 기본 능력치 합 - 구매 전 합이며 패시브나 실제 챔피언의 최종 능력치가 아닙니다. 비율은 카탈로그 소수 단위입니다.

v1 JSONL과 모델을 v2 경로에 섞으면 계약 오류로 중단합니다. 기존 v1 결과를 보존하고 새 출력 경로를 사용하세요.

```bash
uv run python -m component_ltr.train --data-dir ../dfgg/build/component-data/observed-core-v2-16.18 --out-dir data/component-model-v2
uv run python -m component_ltr.export_parity_sample --model-dir data/component-model-v2 --data ../dfgg/build/component-data/observed-core-v2-16.18/test.jsonl --out ../dfgg/build/component-model-v2/parity-sample.json
```

### 학습 종료와 관측 구매 불일치 분석

300회 상한에서 계속 개선되었다면 같은 데이터로 상한만 늘려 validation의 early stopping이 종료 시점을 고르게 할 수 있습니다.

```bash
uv run python -m component_ltr.train --data-dir ../dfgg/build/component-data/observed-core-v2-16.18 --out-dir data/component-model-v2-extended --rounds 1200
```

validation-diagnostics.json은 validation만 사용하여 포지션별 관측 구매 적중률, Feature gain, 관측 구매 불일치 사례 10개, 관측 구매와 대안이 동일 Feature를 갖는 그룹 수, 매 학습 회차의 지표를 기록합니다. 호환성을 위해 유지한 `correct`는 관측 구매 일치 건수, `wrongExamples`는 관측 구매 불일치 사례이며 구매 품질 판정이 아닙니다. Feature gain은 인과 효과나 제거 실험 결과가 아닙니다. evaluation.json은 test의 최종 비교 보고서입니다. test의 불일치 사례로 설정을 선택하지 않습니다. 현재 모델은 목표 Core가 주어진 조건부 모델이며 Core LTR 연결은 진행하지 않습니다.

### Component Feature v3: 구매 시점 맥락

v3는 기존 22개 뒤에 game_time_minutes와 position_top/jungle/mid/bottom/support/unknown을 추가한 29개 Feature입니다. 첫 구매 시점의 경과 분이며 목표 Core를 나중에 산 시각을 입력하지 않습니다. 미확정 포지션은 unknown=1입니다. 후보마다 공통인 맥락을 후보 능력치·비용과 함께 판단하도록 전달합니다. Python은 이 값을 다시 계산하지 않습니다.

```bash
uv run python -m component_ltr.train --data-dir ../dfgg/build/component-data/observed-core-v3-16.18 --out-dir data/component-model-v3 --rounds 1200
uv run python -m component_ltr.export_parity_sample --model-dir data/component-model-v3 --data ../dfgg/build/component-data/observed-core-v3-16.18/test.jsonl --out ../dfgg/build/component-model-v3/parity-sample.json
```

현재 loader/configuration은 v3 계약을 검사합니다. v1/v2 파일은 별도로 보존하고 현재 v3 실행 경로에 섞지 않습니다.

v3의 시간·포지션 제거 실험은 같은 JSONL을 사용하고 --without-context로 뒤 7개 열만 0으로 고정합니다. 평가 보고서의 context_features_enabled로 두 실행을 구분합니다.

```bash
uv run python -m component_ltr.train --data-dir ../dfgg/build/component-data/observed-core-v3-16.18 --out-dir data/component-model-v3-no-context --rounds 1200 --without-context
```

v3 생성 시 능력치 차이는 유지된 아이템을 먼저 상쇄하고 변한 수량만 아이템 ID 순서로 합산합니다. 합산 순서에 따른 작은 수치 오차를 Feature로 학습하는 일을 막습니다. 과거 v2 모델과의 비교에는 이 보정 차이도 있으므로, 시간·포지션 효과는 같은 v3 데이터의 제거 실험으로 판단합니다.

### Component 파일별 책임

- dataset.py: Java JSONL을 Feature·라벨·구매 그룹으로 읽고 계약 검사
- train.py: 데이터 분할 확인·학습·모델 export·파일 저장 조율
- evaluate.py: 구매 그룹별 Hit@1/3·MRR·NDCG 계산
- analyze.py: validation의 관측 구매 불일치·포지션별 성능·Feature 중복·중요도 보고서
- export_parity_sample.py: 원본 Python 모델과 Java 예측을 비교할 표본 생성

기존 python -m component_ltr.train 명령과 출력 파일은 그대로입니다. 학습을 다시 하지 않고도 evaluate와 analyze의 함수를 호출해 저장된 모델을 검사할 수 있습니다.

`component_ltr/feature_schema.py`는 Feature 이름·순서·버전·지문을 관리합니다.
`dataset.py`는 이 계약으로 JSONL을 검증하고 학습 배열을 만듭니다. Feature 계산은 Java에서 수행합니다.

## Component v4 계약 검증·산출물

학습 전에 모든 분할을 검증하고 `dataset-validation.json`에 첫 실패 규칙별 건수와 행 번호를 남깁니다. 오류 행을 자동 제외하지 않으며 하나라도 실패하면 학습을 중단합니다. 경기 중복과 기존 manifest의 분할·묶음 수를 대조합니다. JSONL은 schemaVersion으로, 모델은 modelVersion으로 구분합니다.

현재 objective는 Core와 같은 `lambdarank`이며 데이터 단위는 QUERY_LIST입니다. 순수 List-wise loss라는 의미는 아닙니다. NDCG@1/3, Hit@1/3, MRR과 COST_DESC·CHEAPEST·MOST_MATERIAL_CONSUMED 기준선을 같은 후보로 비교합니다. MAX_CORE_PROGRESS는 진행도 정의가 없어 보류합니다.

기존 booster.txt·model.json·evaluation.json·validation-diagnostics.json에 metadata.json·feature_importance.csv·split_manifest.json·dataset-validation.json을 추가합니다. `OBSERVED_FUTURE_CORE` 실험 모델은 metadata.json과 model.json에 deployable=false를 기록합니다. 운영 연결은 별도 단계입니다. Feature·후보·분할 정책은 변경하지 않습니다.

### Component cohort weight experiment

이 절의 가중치·cutoff는 오프라인 실험 정책입니다. 현재 일반 학습에서는 동일 Weight를 유지합니다. COLLECTION_COHORT 실험에서 숙련자 수집 집단의 관측 구매 예측 개선 근거를 확보하지 못했으며, 이 metadata는 개별 참가자의 경기 당시 실제 티어가 아닙니다.

`component_ltr.tier_weight_experiment` compares baseline, mild, strong,
DIAMOND+ and MASTER+ train cutoffs on the **same original match split** and
41 baseline feature columns. Labels and candidates are unchanged; validation
and test use every original query without weights.

```bash
uv run python -m component_ltr.tier_weight_experiment \
  --data-dir ../dfgg/build/component-data/observed-target-v5-16.18 \
  --tier-csv ../dfgg/build/component-tier-weight-input/cohort-tiers.csv \
  --out-dir ../dfgg/build/component_ltr_tier_weight_experiment \
  --rounds 1200 --allow-cohort
```

The CSV columns are `matchId,participantId,tier,tierSource,observedAt`.
The current tier definition is **match collection cohort**, explicitly confirmed
by the user; use source `COLLECTION_COHORT`, with observation time blank.
All participants in a match must have one cohort. This does not measure
individual player rank. The experiment never changes normalized DB records.
Verified individual-rank snapshots are also supported via `MATCH_TIME_RANK`
or `PLAYER_OBSERVED_RANK`, with an observation timestamp; sources cannot mix
cohort and player ranks. Missing or duplicate tier records abort the run.

The output directory must be empty. It contains per-policy model/evaluation
artifacts, fixed split manifests, metrics CSVs, baseline-universe target coverage
(including targets lost to cutoffs), a report answering Q1–Q8, input SHA-256
hashes and library/configuration versions. Missing core-stage metadata skips
that stratification. Empty strata have null metrics and explicit group counts.
Models have `deployable=false`; this is an experiment, not a production change.

## Component Game Context v1 — 현재 v6 계약

현재 계약은 `component-bundle-validation-v6` / `component-ltr-v6`, 55개 Feature다.
기존 46개 열 뒤에 Core와 공통으로 계산하는 적 태그 5개와 자신 제외 아군 태그 4개를 추가한다.
Data Dragon 태그 개수이며 실제 Damage/CC/Healing/Range를 뜻하지 않는다.
`CompositionFeatureCalculator`를 Core, Component 학습, Component Serving이 공유한다.
Python은 조합을 다시 계산하지 않으며 Query 내 9개 값의 동일성도 검사한다.

기존 v5 데이터/모델은 현재 로더에서 거절한다. 이전 결과를 덮어쓰지 말고 새 경로를 사용한다.
문서 위의 v1~v5 명령과 평가 기록은 당시 계약에 대한 이력이다.

기존 v5 후보/Label/분할/46열을 보존한 비교 데이터는 Java에서 생성할 수 있다.
참가자 조합과 Champion metadata가 있는 DB를 TEST_DB_URL/USERNAME/PASSWORD로 지정한다.
읽기 전용 export이며 기존 통계를 재집계하지 않는다.

```bash
# backend/dfgg; 출력 디렉터리는 비어 있어야 한다.
./gradlew evaluationTest --tests '*ComponentGameContextExportTest' \
  -Devaluation.component.source="$PWD/build/component-data/observed-target-v5-16.18" \
  -Devaluation.component.output="$PWD/build/component-data/game-context-v6-16.18"

python3 scripts/run-component-game-context-experiment.py \
  --data-dir build/component-data/game-context-v6-16.18 \
  --out-dir build/component-game-context-v6-experiment --rounds 1200
```

직접 새 구매 데이터에서 export할 때는 기존 `ComponentDatasetExportTest`를 사용한다.
해당 DB에 이벤트와 정규화 참가자의 teamId/championId가 함께 있어야 한다.
과거의 championId만 있는 보조 참가자 파일은 경기 조합을 대신할 수 없다.
참가자 조합 자체가 없으면 export를 중단하고, 태그가 없는 챔피언은 기존 Core처럼 0 기여로 계산한다.

`--without-composition`은 같은 v6 데이터에서 마지막 9개를 실제로 제거해 46개 baseline을 학습한다.
이 모델은 `component-ltr-v6-ablation`이며 55열 Serving 계약과 호환되지 않는다.
일반 `component_ltr.train`은 모든 55개를 사용한다.
두 실행의 후보/Label/분할/seed는 같고 validation early stopping만 사용한다.
Role/Target Item/기존 Core stage/관측 후보 source slice는 evaluation.json에 기록한다.

champion-tags.json과 그 SHA-256을 dataset manifest 및 모델 metadata에 기록한다.
학습 전에 파일 해시를 검증하며 모델 디렉터리에도 복사한다.
Serving은 같은 Champion metadata를 사용해야 한다. DB에는 패치별 태그 이력이 없으며,
현재 런타임이 모델 해시와 DB 태그를 자동 대조하는 기능은 없다.

Target은 기존 `OBSERVED_FUTURE_TARGET_ITEM`을 유지한다. 이 모델의 성능은 해당 Target이
주어진 조건부 관측 구매 재현 지표이고 실제 Core 연결 평가나 승률 개선의 증거가 아니다.
기존과 같이 `deployable=false`로 저장하며 운영 모델 설정을 자동 교체하지 않는다.
