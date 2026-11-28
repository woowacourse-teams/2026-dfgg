"""모델이 어느 묶음에 실제로 기대는지 잰다.

두 방식을 같이 본다. SHAP은 "예측을 얼마나 밀었나"(크기)를, permutation은 "그 값이 없으면
지표가 얼마나 나빠지나"(쓸모)를 말한다. counter처럼 값은 크지만 예측력은 없는 경우 둘이
갈리므로, 하나만 보면 오독한다.
"""
import lightgbm as lgb
import numpy as np
import pytest

from dfgg_ltr.importance import permutation_importance_by_group, shap_importance_by_group


@pytest.fixture
def booster_and_data():
    rng = np.random.default_rng(3)
    features = rng.random((600, 4))
    # f0만 라벨을 결정한다. f2, f3는 순수 잡음이다.
    labels = (features[:, 0] * 3.99).astype(int)
    dataset = lgb.Dataset(features, label=labels, group=[10] * 60,
                          feature_name=[f"f{i}" for i in range(4)])
    booster = lgb.train(
        {"objective": "lambdarank", "ndcg_eval_at": [5], "num_leaves": 15,
         "verbose": -1, "feature_pre_filter": False},
        dataset, num_boost_round=40)
    return booster, features, labels, [10] * 60


GROUPS = {"SIGNAL": [0], "NOISE": [2, 3]}


class TestShapImportanceByGroup:
    def test_reports_one_value_per_group(self, booster_and_data):
        booster, features, _, _ = booster_and_data

        importance = shap_importance_by_group(booster, features, GROUPS)

        assert set(importance) == {"SIGNAL", "NOISE"}

    def test_the_group_that_drives_the_label_dominates(self, booster_and_data):
        booster, features, _, _ = booster_and_data

        importance = shap_importance_by_group(booster, features, GROUPS)

        assert importance["SIGNAL"] > importance["NOISE"]

    def test_values_are_non_negative_because_it_averages_magnitudes(self, booster_and_data):
        booster, features, _, _ = booster_and_data

        importance = shap_importance_by_group(booster, features, GROUPS)

        assert all(value >= 0 for value in importance.values())

    def test_rejects_a_group_referring_to_a_column_the_model_does_not_have(self, booster_and_data):
        booster, features, _, _ = booster_and_data

        with pytest.raises(ValueError):
            shap_importance_by_group(booster, features, {"BAD": [99]})


class TestPermutationImportanceByGroup:
    def test_reports_the_metric_drop_for_each_group(self, booster_and_data):
        booster, features, labels, group = booster_and_data

        importance = permutation_importance_by_group(
            booster, features, labels, group, GROUPS, repeats=2, seed=1)

        assert set(importance) == {"SIGNAL", "NOISE"}

    def test_shuffling_the_signal_hurts_more_than_shuffling_noise(self, booster_and_data):
        booster, features, labels, group = booster_and_data

        importance = permutation_importance_by_group(
            booster, features, labels, group, GROUPS, repeats=2, seed=1)

        assert importance["SIGNAL"] > importance["NOISE"]

    def test_is_reproducible_for_a_given_seed(self, booster_and_data):
        booster, features, labels, group = booster_and_data

        first = permutation_importance_by_group(
            booster, features, labels, group, GROUPS, repeats=2, seed=7)
        second = permutation_importance_by_group(
            booster, features, labels, group, GROUPS, repeats=2, seed=7)

        assert first == second

    def test_does_not_modify_the_caller_s_feature_matrix(self, booster_and_data):
        """섞은 값을 되돌리지 않으면 이후 분석이 전부 오염된다."""
        booster, features, labels, group = booster_and_data
        original = features.copy()

        permutation_importance_by_group(
            booster, features, labels, group, GROUPS, repeats=1, seed=1)

        np.testing.assert_array_equal(features, original)
