"""평가 결과를 표로 옮기는 부분. 숫자를 만드는 쪽(metrics)과 분리해 각각 검증한다."""
import numpy as np
import pytest

from dfgg_ltr.dataset import Dataset
from dfgg_ltr.evaluate import baseline_scores, render_report


def make_dataset() -> Dataset:
    return Dataset(
        features=np.array([[0.9, 0.1], [0.2, 0.8], [0.5, 0.5]]),
        labels=np.array([3, 0, 1]),
        group=np.array([3]),
        qids=["q1"],
        feature_names=["build_score", "counter_score"],
        schema_fingerprint="abc",
    )


class TestBaselineScores:
    def test_uses_the_named_feature_column_as_the_score(self):
        scores = baseline_scores(make_dataset(), "build_score")

        np.testing.assert_array_equal(scores, np.array([0.9, 0.2, 0.5]))

    def test_rejects_a_feature_that_is_not_in_the_schema(self):
        with pytest.raises(ValueError):
            baseline_scores(make_dataset(), "no_such_feature")

    def test_random_baseline_is_reproducible_for_a_given_seed(self):
        first = baseline_scores(make_dataset(), "random", seed=7)
        second = baseline_scores(make_dataset(), "random", seed=7)

        np.testing.assert_array_equal(first, second)

    def test_missing_values_rank_last_rather_than_being_treated_as_high(self):
        """결측(NaN)은 정렬에서 앞으로 튀면 안 된다. 단일 feature 베이스라인이 왜곡된다."""
        dataset = make_dataset()
        dataset.features[0, 0] = np.nan

        scores = baseline_scores(dataset, "build_score")

        assert scores[0] == -np.inf


class TestRenderReport:
    def test_renders_one_row_per_method_with_its_metrics(self):
        report = render_report(
            title="game split",
            rows=[("LTR", {"ndcg@1": 0.71, "ndcg@5": 0.87, "mrr": 0.8,
                           "hit_rate@5": 0.9, "queries": 100})],
        )

        assert "game split" in report
        assert "LTR" in report
        assert "0.7100" in report

    def test_orders_rows_as_given_so_the_headline_method_stays_first(self):
        rows = [("LTR", {"ndcg@1": 0.1, "ndcg@5": 0.1, "mrr": 0.1, "hit_rate@5": 0.1, "queries": 1}),
                ("build_score", {"ndcg@1": 0.9, "ndcg@5": 0.9, "mrr": 0.9, "hit_rate@5": 0.9, "queries": 1})]

        report = render_report(title="t", rows=rows)

        assert report.index("LTR") < report.index("build_score")

    def test_reports_the_query_count_so_a_tiny_test_set_is_visible(self):
        report = render_report(
            title="t",
            rows=[("LTR", {"ndcg@1": 0.5, "ndcg@5": 0.5, "mrr": 0.5,
                           "hit_rate@5": 0.5, "queries": 42})],
        )

        assert "42" in report
