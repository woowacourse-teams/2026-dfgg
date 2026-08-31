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

## 사용 (미구현 — Task 11)

학습 데이터는 **Java가 만든다**. 먼저 `backend/dfgg`에서:

```bash
./gradlew test --tests '*TrainingSetExport*' -Dtest.tags=evaluation
```

이 테스트가 `../ml/data/train.jsonl`을 생성한다. 그 다음:

```bash
uv run python train.py --data data/train.jsonl --split game
uv run python export_model.py --out ../dfgg/src/main/resources/ltr/model.json
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
- `export_model.py`가 `dump_model()`의 중첩 트리를 **평탄 배열로 변환**해 내보낸다
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
