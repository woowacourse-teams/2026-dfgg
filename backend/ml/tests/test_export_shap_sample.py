"""Java TreeSHAP이 맞는지 판정할 기준값을 만든다.

기준은 참조 구현이 아니라 **LightGBM 자체**(`pred_contrib=True`)다. TreeSHAP을 Python과 Java에
두 번 구현하면 둘 다 같은 방식으로 틀릴 수 있어서, 학습 시 네이티브 모델을 함께 저장해 두고
그것을 기준으로 삼는다.
"""
import json

import lightgbm as lgb
import numpy as np
import pytest

from dfgg_ltr.export_shap_sample import build_shap_parity_sample


@pytest.fixture
def booster():
    rng = np.random.default_rng(5)
    features = rng.random((300, 6))
    features[rng.random(features.shape) < 0.2] = np.nan  # 결측 경로도 덮는다
    labels = rng.integers(0, 4, 300)
    dataset = lgb.Dataset(features, label=labels, group=[20] * 15,
                          feature_name=[f"f{i}" for i in range(6)])
    return lgb.train(
        {"objective": "lambdarank", "ndcg_eval_at": [5], "num_leaves": 9,
         "verbose": -1, "feature_pre_filter": False},
        dataset, num_boost_round=5)


class TestBuildShapParitySample:
    def test_each_case_carries_one_contribution_per_feature_plus_base(self, booster):
        sample = build_shap_parity_sample(booster, [[0.1] * 6], fingerprint="abc")

        assert len(sample["cases"][0]["expected_contributions"]) == 7

    def test_contributions_sum_to_the_raw_prediction(self, booster):
        """SHAP의 정의다. 이게 깨지면 기여도를 '이유'라고 부를 수 없다."""
        rows = [[0.2, 0.4, 0.6, 0.8, 0.1, 0.3]]

        sample = build_shap_parity_sample(booster, rows, fingerprint="abc")

        case = sample["cases"][0]
        assert sum(case["expected_contributions"]) == pytest.approx(
            case["expected_score"], abs=1e-9)

    def test_expected_score_matches_the_boosters_raw_prediction(self, booster):
        rows = [[0.2, 0.4, 0.6, 0.8, 0.1, 0.3]]

        sample = build_shap_parity_sample(booster, rows, fingerprint="abc")

        assert sample["cases"][0]["expected_score"] == pytest.approx(
            booster.predict(np.array(rows), raw_score=True)[0])

    def test_missing_values_stay_null_so_java_reads_them_as_nan(self, booster):
        sample = build_shap_parity_sample(booster, [[None] * 6], fingerprint="abc")

        assert sample["cases"][0]["features"] == [None] * 6

    def test_is_json_serializable_without_nan(self, booster):
        sample = build_shap_parity_sample(booster, [[None] * 6], fingerprint="abc")

        assert json.loads(json.dumps(sample, allow_nan=False)) == sample

    def test_rejects_rows_whose_width_differs_from_the_model(self, booster):
        with pytest.raises(ValueError):
            build_shap_parity_sample(booster, [[0.1, 0.2]], fingerprint="abc")
