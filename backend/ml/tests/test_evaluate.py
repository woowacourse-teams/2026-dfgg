"""평가 결과를 표로 옮기는 부분. 숫자를 만드는 쪽(metrics)과 분리해 각각 검증한다."""
import numpy as np
import pytest

import json

from dfgg_ltr.dataset import Dataset, QueryMeta
from dfgg_ltr.evaluate import (
    baseline_scores,
    load_propensities,
    model_scores,
    render_report,
    render_value_report,
    value_rows,
    value_section,
)


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


def make_value_inputs():
    """query 4개(후보 2개씩). 0·1번은 첫 후보가 정답, 2·3번은 둘째 후보가 정답."""
    dataset = Dataset(
        features=np.zeros((8, 1)), labels=np.array([3, 0, 3, 0, 0, 3, 0, 3]), group=np.array([2, 2, 2, 2]),
        qids=["q0", "q1", "q2", "q3"], feature_names=["f"], schema_fingerprint="abc")
    meta = QueryMeta(qids=dataset.qids, match_ids=["A", "B", "C", "D"], patches=["16.22"] * 4, win=np.array([1.0, 1.0, 0.0, 0.0]),
                     purchase_minute=np.full(4, np.nan), team_gold_diff=np.full(4, np.nan),
                     own_total_gold=np.full(4, np.nan))
    first = np.array([1.0, 0.0] * 4)
    second = np.array([0.0, 1.0] * 4)
    return dataset, meta, first, second


class TestValueReport:
    def test_load_propensities_follows_query_order_and_marks_missing_as_nan(self, tmp_path):
        path = tmp_path / "propensity-game.jsonl"
        path.write_text("\n".join(json.dumps(row) for row in [
            {"qid": "q2", "propensity": 0.4}, {"qid": "q1", "propensity": None}]), encoding="utf-8")

        propensities = load_propensities(path, ["q1", "q2", "q3"])

        assert np.isnan(propensities[0]) and propensities[1] == 0.4 and np.isnan(propensities[2])

    def test_model_scores_rejects_a_model_built_for_another_schema(self):
        with pytest.raises(ValueError, match="스키마 지문"):
            model_scores({"schema_fingerprint": "other", "trees": []}, make_dataset())

    def test_model_scores_walks_the_exported_trees(self):
        tree = {"split_feature": [0], "threshold": [0.5], "default_left": [True],
                "left": [-1], "right": [-2], "leaf_value": [-1.0, 1.0]}

        scores = model_scores({"schema_fingerprint": "abc", "trees": [tree]}, make_dataset())

        np.testing.assert_array_equal(scores, np.array([1.0, -1.0, -1.0]))

    def test_value_rows_compare_each_method_with_the_reference(self):
        dataset, meta, first, second = make_value_inputs()

        rows = dict(value_rows(dataset, meta, np.full(4, 0.5), [("ref", first), ("other", second)], "ref"))

        assert rows["ref"]["value"].value == 1.0
        assert rows["ref"]["difference"]["difference"] == 0.0
        assert rows["other"]["value"].value == 0.0
        assert rows["other"]["difference"]["difference"] == -1.0
        assert rows["ref"]["hit_rate@5_win"] == 1.0

    def test_render_value_report_shows_value_difference_and_observed_win(self):
        dataset, meta, first, second = make_value_inputs()
        rows = value_rows(dataset, meta, np.full(4, 0.5), [("ref", first), ("other", second)], "ref")

        report = render_value_report("game split", rows, "ref")

        assert "기준: ref" in report
        assert "| other | 0.0000" in report
        assert "-100.00" in report
        assert "관측 승률(같은 query): 0.5000" in report

    def test_value_section_explains_why_it_is_skipped_without_propensities(self, tmp_path):
        message = value_section(tmp_path / "train.jsonl", tmp_path / "schema.json", "game", tmp_path, [], "")

        assert message.startswith("V 생략") and "propensity-game.jsonl" in message
