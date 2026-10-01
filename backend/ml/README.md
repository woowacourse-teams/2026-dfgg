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

기존 Python 3.12와 uv 환경을 공유합니다. Java가 만든 train/validation/test.jsonl을 같은 디렉터리에 놓습니다.

```bash
uv sync
uv run python -m component_ltr.train --data-dir ../dfgg/build/component-data/observed-core-16.18 --out-dir data/component-model
uv run pytest tests/test_component_dataset.py
```

component_ltr.dataset은 한 구매 묶음을 한 비교 그룹으로 읽습니다. 기존 Core Dataset 자료형을 재사용하고, 0/1 라벨·정답 하나·Feature 순서·중복 후보를 검사합니다. train은 학습, validation은 early stopping, test는 최종 평가에만 사용합니다. 경기 중복을 발견하면 중단합니다.

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

### 학습 종료와 오답 분석

300회 상한에서 계속 개선되었다면 같은 데이터로 상한만 늘려 validation의 early stopping이 종료 시점을 고르게 할 수 있습니다.

```bash
uv run python -m component_ltr.train --data-dir ../dfgg/build/component-data/observed-core-v2-16.18 --out-dir data/component-model-v2-extended --rounds 1200
```

validation-diagnostics.json은 validation만 사용하여 포지션별 적중률, Feature gain, 오답 10개, 정답과 다른 후보가 동일 Feature를 갖는 그룹 수, 매 학습 회차의 지표를 기록합니다. Feature gain은 인과 효과나 제거 실험 결과가 아닙니다. evaluation.json은 test의 최종 비교 보고서입니다. test 오답으로 설정을 선택하지 않습니다. 현재 모델은 목표 Core가 주어진 조건부 모델이며 Core LTR 연결은 진행하지 않습니다.

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
- analyze.py: validation의 오답·포지션별 성능·Feature 중복·중요도 보고서
- export_parity_sample.py: 원본 Python 모델과 Java 예측을 비교할 표본 생성

기존 python -m component_ltr.train 명령과 출력 파일은 그대로입니다. 학습을 다시 하지 않고도 evaluate와 analyze의 함수를 호출해 저장된 모델을 검사할 수 있습니다.

`component_ltr/feature_schema.py`는 Feature 이름·순서·버전·지문을 관리합니다.
`dataset.py`는 이 계약으로 JSONL을 검증하고 학습 배열을 만듭니다. Feature 계산은 Java에서 수행합니다.

## Component v4 계약 검증·산출물

학습 전에 모든 분할을 검증하고 `dataset-validation.json`에 첫 실패 규칙별 건수와 행 번호를 남깁니다. 오류 행을 자동 제외하지 않으며 하나라도 실패하면 학습을 중단합니다. 경기 중복과 기존 manifest의 분할·묶음 수를 대조합니다. JSONL은 schemaVersion으로, 모델은 modelVersion으로 구분합니다.

현재 objective는 Core와 같은 `lambdarank`이며 데이터 단위는 QUERY_LIST입니다. 순수 List-wise loss라는 의미는 아닙니다. NDCG@1/3, Hit@1/3, MRR과 COST_DESC·CHEAPEST·MOST_MATERIAL_CONSUMED 기준선을 같은 후보로 비교합니다. MAX_CORE_PROGRESS는 진행도 정의가 없어 보류합니다.

기존 booster.txt·model.json·evaluation.json·validation-diagnostics.json에 metadata.json·feature_importance.csv·split_manifest.json·dataset-validation.json을 추가합니다. `OBSERVED_FUTURE_CORE` 실험 모델은 metadata.json과 model.json에 deployable=false를 기록합니다. 운영 연결은 별도 단계입니다. Feature·후보·분할 정책은 변경하지 않습니다.
