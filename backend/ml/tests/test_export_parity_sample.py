"""Java parity 테스트가 읽을 fixture를 만든다.

fixture의 기대 점수는 Java가 로드하는 것과 <b>같은</b> model.json에서 참조 구현으로 계산한다.
그래야 Java 쪽이 어긋났을 때 원인이 모델 형식이 아니라 Java 순회로 좁혀진다.
"""
import json

import pytest

from dfgg_ltr.export_parity_sample import build_parity_sample, rows_for_model
from dfgg_ltr.reference_predict import predict_with_flat_trees

# f0 <= 0.5 → 1.0, 아니면 2.0 / 결측은 오른쪽
MODEL = {
    "schema_fingerprint": "abc123",
    "feature_names": ["f0", "f1"],
    "objective": "lambdarank",
    "trees": [{
        "split_feature": [0], "threshold": [0.5], "default_left": [False],
        "left": [-1], "right": [-2], "leaf_value": [1.0, 2.0],
    }],
}


def test_expected_scores_come_from_the_reference_traversal():
    rows = [[0.1, 0.2], [0.9, 0.2]]

    sample = build_parity_sample(MODEL, rows)

    assert [case["expected_score"] for case in sample["cases"]] == [1.0, 2.0]


def test_missing_values_survive_as_null_and_route_by_default_left():
    """JSON에 NaN을 쓸 수 없으므로 null로 싣는다. 결측이 0으로 뭉개지면 이 값이 1.0이 된다."""
    sample = build_parity_sample(MODEL, [[None, 0.2]])

    assert sample["cases"][0]["features"] == [None, 0.2]
    assert sample["cases"][0]["expected_score"] == 2.0


def test_carries_the_schema_fingerprint_so_java_can_reject_a_stale_fixture():
    sample = build_parity_sample(MODEL, [[0.1, 0.2]])

    assert sample["schema_fingerprint"] == "abc123"


def test_rejects_a_row_whose_length_does_not_match_the_schema():
    with pytest.raises(ValueError):
        build_parity_sample(MODEL, [[0.1]])


def test_is_json_serializable_without_nan():
    """allow_nan=False로 직렬화돼야 한다. NaN 리터럴은 표준 JSON이 아니라 Jackson이 못 읽는다."""
    sample = build_parity_sample(MODEL, [[None, 0.2]])

    assert json.loads(json.dumps(sample, allow_nan=False)) == sample


def test_scores_stay_consistent_for_many_random_rows():
    import random
    rng = random.Random(3)
    rows = [[rng.choice([None, rng.random()]), rng.random()] for _ in range(50)]

    sample = build_parity_sample(MODEL, rows)

    for case in sample["cases"]:
        row = [float("nan") if v is None else v for v in case["features"]]
        assert case["expected_score"] == pytest.approx(
            predict_with_flat_trees(MODEL["trees"], row), abs=1e-12)


class TestThresholdBoundaryRows:
    """실데이터에서 뽑은 값은 LightGBM 임계값(관측값 사이 중간점)과 정확히 같아지는 일이 거의 없다.

    그래서 무작위 표본만으로는 `<=`의 경계가 한 번도 밟히지 않고, `<`로 잘못 구현해도 통과한다.
    실제로 변이 테스트에서 이 구멍을 확인했다. 경계를 정확히 때리는 행을 따로 만들어 넣는다.
    """

    def test_produces_a_row_whose_value_equals_a_model_threshold_exactly(self):
        from dfgg_ltr.export_parity_sample import threshold_boundary_rows

        rows = threshold_boundary_rows(MODEL)

        assert any(row[0] == 0.5 for row in rows)

    def test_boundary_rows_have_the_schema_length(self):
        from dfgg_ltr.export_parity_sample import threshold_boundary_rows

        rows = threshold_boundary_rows(MODEL)

        assert rows and all(len(row) == len(MODEL["feature_names"]) for row in rows)

    def test_features_the_model_never_splits_on_stay_missing(self):
        """f1로는 분기하지 않는다. 임의값을 채우기보다 결측으로 두는 편이 정직하다."""
        from dfgg_ltr.export_parity_sample import threshold_boundary_rows

        rows = threshold_boundary_rows(MODEL)

        assert all(row[1] is None for row in rows)

    def test_sample_reports_how_many_boundary_cases_it_carries(self):
        """Java가 이 수를 확인해, 경계 없는 fixture로 게이트가 헐거워지는 일을 막는다."""
        from dfgg_ltr.export_parity_sample import build_parity_sample, threshold_boundary_rows

        rows = threshold_boundary_rows(MODEL)
        sample = build_parity_sample(MODEL, rows, boundary_case_count=len(rows))

        assert sample["boundary_case_count"] == len(rows)


class TestRowsForModel:
    """스키마 뒤에 feature가 더해진 뒤에도, 앞쪽 feature(기준 계약)로 학습한 모델의 fixture를 만들 수 있어야 한다."""

    SCHEMA_NAMES = ["f0", "f1", "f2"]

    def test_keeps_rows_that_already_match_the_model(self):
        model = {"feature_names": ["f0", "f1", "f2"]}
        rows = [[1.0, None, 3.0]]

        assert rows_for_model(rows, model, self.SCHEMA_NAMES) == rows

    def test_cuts_longer_rows_down_to_the_models_leading_features(self):
        """학습 줄이 모델보다 길면 모델이 본 앞쪽 칸까지만 쓴다. 칸 번호가 그대로라 값이 어긋나지 않는다."""
        model = {"feature_names": ["f0", "f1"]}

        assert rows_for_model([[1.0, None, 3.0], [4.0, 5.0, 6.0]], model, self.SCHEMA_NAMES) == [
            [1.0, None], [4.0, 5.0]]

    def test_refuses_to_cut_when_the_model_is_not_trained_on_the_leading_features(self):
        """중간 feature를 뺀 모델에 앞쪽 칸을 주면 엉뚱한 값으로 기대 점수를 만든다. 멈추고 알린다."""
        model = {"feature_names": ["f0", "f2"]}

        with pytest.raises(ValueError, match="앞쪽"):
            rows_for_model([[1.0, 2.0, 3.0]], model, self.SCHEMA_NAMES)

    def test_refuses_to_cut_without_a_schema_to_check_against(self):
        model = {"feature_names": ["f0", "f1"]}

        with pytest.raises(ValueError):
            rows_for_model([[1.0, 2.0, 3.0]], model, None)
