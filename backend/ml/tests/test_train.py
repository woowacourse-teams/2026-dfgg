"""학습 명령의 선택지 검증 — "앞쪽 feature만"과 씨앗.

지금 운영 모델은 스키마의 앞쪽 feature(기준 계약)로 학습됐다. 스키마 뒤에 feature가 더해진 뒤에도
그 모델을 다시 학습하려면 "앞쪽만 쓰고, 기준 계약의 이름·지문으로 내보내는" 길이 있어야 한다.
그렇게 내보내지 않으면 서버가 지문에서 거부한다.
"""
import json

import numpy as np
import pytest

from dfgg_ltr.train import main

FEATURE_NAMES = ["f0", "f1", "f2", "f3"]
BASELINE_COUNT = 3


@pytest.fixture
def schema(tmp_path):
    path = tmp_path / "feature_schema.json"
    path.write_text(json.dumps({
        "schema_fingerprint": "full789", "feature_count": len(FEATURE_NAMES),
        "baseline_schema_fingerprint": "base456", "baseline_feature_count": BASELINE_COUNT,
        "feature_names": FEATURE_NAMES,
    }), encoding="utf-8")
    return path


@pytest.fixture
def data(tmp_path):
    """query 400개 × 후보 6개. 정답은 f0과 f3(기준 계약 뒤의 feature)이 함께 정한다."""
    rng = np.random.default_rng(0)
    path = tmp_path / "train.jsonl"
    with path.open("w", encoding="utf-8") as out:
        for query in range(400):
            features = rng.random((6, len(FEATURE_NAMES)))
            best = int(np.argmax(features[:, 0] + features[:, 3]))
            for candidate in range(6):
                out.write(json.dumps({
                    "qid": f"q{query}", "label": 3 if candidate == best else 0,
                    "item_id": 1000 + candidate, "match_id": f"M{query}", "patch": "16.16",
                    "champion_id": 157, "position": "MID", "purchase_step": 0,
                    "split_game": "train" if query < 300 else "test", "split_patch": "train",
                    "features": features[candidate].tolist(),
                }) + "\n")
    return path


def train_and_read(tmp_path, data, schema, name, *options):
    out = tmp_path / f"{name}.json"
    main(["--data", str(data), "--schema", str(schema), "--rounds", "30", "--out", str(out), *options])
    return json.loads(out.read_text(encoding="utf-8"))


def split_features_of(model):
    return {feature for tree in model["trees"] for feature in tree["split_feature"]}


def test_baseline_option_exports_the_baseline_names_and_fingerprint(tmp_path, data, schema):
    """기준 계약으로 학습한 모델은 그 계약의 이름·지문으로 나간다 — 서버가 기준 계약으로 읽는 모양이다."""
    model = train_and_read(tmp_path, data, schema, "baseline", "--baseline")

    assert model["feature_names"] == FEATURE_NAMES[:BASELINE_COUNT]
    assert model["schema_fingerprint"] == "base456"


def test_baseline_option_never_splits_on_features_after_the_baseline(tmp_path, data, schema):
    """기준 모델의 트리는 기준 계약 뒤의 칸을 가리키지 않는다 — f3이 정답을 정해도 볼 수 없다."""
    model = train_and_read(tmp_path, data, schema, "baseline", "--baseline")

    assert max(split_features_of(model)) < BASELINE_COUNT


def test_without_baseline_option_trains_on_the_full_schema(tmp_path, data, schema):
    """선택지를 주지 않으면 지금처럼 스키마 전체로 학습한다."""
    model = train_and_read(tmp_path, data, schema, "full")

    assert model["feature_names"] == FEATURE_NAMES
    assert model["schema_fingerprint"] == "full789"
    assert FEATURE_NAMES.index("f3") in split_features_of(model)


def test_same_seed_gives_the_same_model(tmp_path, data, schema):
    """같은 씨앗이면 같은 모델이 나온다 — 두 모델의 차이를 씨앗 탓으로 돌릴 수 있으려면 재현돼야 한다."""
    first = train_and_read(tmp_path, data, schema, "first", "--seed", "7")
    second = train_and_read(tmp_path, data, schema, "second", "--seed", "7")

    assert first == second


def test_different_seed_gives_a_different_model(tmp_path, data, schema):
    """씨앗이 다르면 모델이 달라진다 — 이 차이가 "같은 데이터로 다시 학습했을 때의 흔들림"이다."""
    first = train_and_read(tmp_path, data, schema, "first", "--seed", "7")
    second = train_and_read(tmp_path, data, schema, "second", "--seed", "8")

    assert first != second


def test_without_seed_option_training_stays_reproducible(tmp_path, data, schema):
    """씨앗을 주지 않으면 LightGBM의 고정 기본값을 쓴다 — 기존 학습 결과가 바뀌지 않는다."""
    first = train_and_read(tmp_path, data, schema, "first")
    second = train_and_read(tmp_path, data, schema, "second")

    assert first == second
