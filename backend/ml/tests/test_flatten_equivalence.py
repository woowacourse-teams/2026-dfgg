"""평탄화한 트리가 LightGBM 원본과 같은 예측을 내는지 검증한다.

T12에서 Java 로더를 만들 때 이 규약을 그대로 구현하므로, 여기서 규약 자체가 옳은지 먼저
확인해 둔다. 이게 틀려 있으면 Java parity 테스트가 실패해도 원인이 Java인지 형식인지 모른다.
"""
import lightgbm as lgb
import numpy as np
import pytest

from dfgg_ltr.model_export import flatten_model


def predict_with_flat_trees(trees, row):
    """Java가 구현할 순회를 Python으로 그대로 흉내낸다."""
    total = 0.0
    for tree in trees:
        if not tree["split_feature"]:
            total += tree["leaf_value"][0]
            continue
        node = 0
        while node >= 0:
            value = row[tree["split_feature"][node]]
            if np.isnan(value):
                go_left = tree["default_left"][node]
            else:
                go_left = value <= tree["threshold"][node]
            node = tree["left"][node] if go_left else tree["right"][node]
        total += tree["leaf_value"][-node - 1]
    return total


@pytest.fixture
def booster_and_data():
    rng = np.random.default_rng(7)
    features = rng.random((400, 8))
    # 결측을 섞어 default_left 경로까지 검증한다
    features[rng.random(features.shape) < 0.15] = np.nan
    labels = rng.integers(0, 4, 400)
    dataset = lgb.Dataset(features, label=labels, group=[20] * 20,
                          feature_name=[f"f{i}" for i in range(8)])
    booster = lgb.train(
        {"objective": "lambdarank", "metric": "ndcg", "ndcg_eval_at": [5],
         "num_leaves": 15, "verbose": -1, "feature_pre_filter": False},
        dataset, num_boost_round=30,
    )
    return booster, features


def test_flat_traversal_matches_lightgbm_prediction(booster_and_data):
    booster, features = booster_and_data
    trees = flatten_model(booster.dump_model())

    expected = booster.predict(features, raw_score=True)
    actual = np.array([predict_with_flat_trees(trees, row) for row in features])

    np.testing.assert_allclose(actual, expected, rtol=0, atol=1e-9)


def test_matches_even_for_rows_that_are_entirely_missing(booster_and_data):
    """전부 NaN인 행도 일치해야 한다 — default_left 경로만 타는 극단이다."""
    booster, features = booster_and_data
    trees = flatten_model(booster.dump_model())
    all_nan = np.full((3, features.shape[1]), np.nan)

    expected = booster.predict(all_nan, raw_score=True)
    actual = np.array([predict_with_flat_trees(trees, row) for row in all_nan])

    np.testing.assert_allclose(actual, expected, rtol=0, atol=1e-9)
