"""후보, 현재 모델 비교(U10) 검증.

game test 전체로 판정하고, 최신 패치는 표본이 충분할 때만 판정한다. 기준은 DR 승률, NDCG@5, HitRate@1이다.
"""
import numpy as np
import pytest

from dfgg_ltr.compare_models import (
    INSUFFICIENT_SAMPLE,
    PASSED,
    REJECTED,
    SliceComparison,
    compare,
    exit_code,
    judge_latest_patch,
    judge_total,
    latest_patch,
    render_summary,
    scores_for,
)
from dfgg_ltr.dataset import Dataset, QueryMeta

CURRENT = {"ndcg@5": 0.83, "hit_rate@1": 0.53}


def dr(difference, current=0.52):
    return {"current": {"value": current}, "candidate": {"value": current + difference},
            "difference": {"difference": difference, "lower": difference - 0.01, "upper": difference + 0.01}}


def comparison(candidate, queries=6000, name="game test 전체", win_rate=dr(0.001)):
    return SliceComparison(name=name, queries=queries, current=CURRENT, candidate=candidate, dr=win_rate)


def test_candidate_within_tolerance_and_better_win_rate_passes():
    judged = judge_total(comparison({"ndcg@5": 0.826, "hit_rate@1": 0.521}), 5000)

    assert judged.failures == []
    assert exit_code([judged]) == PASSED


def test_candidate_below_ndcg_or_hit_rate_tolerance_is_rejected():
    judged = judge_total(comparison({"ndcg@5": 0.82, "hit_rate@1": 0.51}), 5000)

    assert len(judged.failures) == 2
    assert exit_code([judged]) == REJECTED


def test_lower_dr_win_rate_is_rejected_even_with_better_ranking():
    judged = judge_total(comparison({"ndcg@5": 0.9, "hit_rate@1": 0.6}, win_rate=dr(-0.0001)), 5000)

    assert judged.failures == ["DR 승률 -0.01%p < 0 (후보 0.5199, 현재 0.5200)"]
    assert exit_code([judged]) == REJECTED


def test_equal_dr_win_rate_passes():
    judged = judge_total(comparison(CURRENT, win_rate=dr(0.0)), 5000)

    assert exit_code([judged]) == PASSED


def test_missing_dr_win_rate_is_insufficient_sample():
    judged = judge_total(comparison(CURRENT, win_rate=None), 5000)

    assert exit_code([judged]) == INSUFFICIENT_SAMPLE
    assert judged.failures[0].startswith("표본 부족: DR 승률을 잴 수 없다")


def test_too_few_total_queries_is_insufficient_sample():
    judged = judge_total(comparison(CURRENT, queries=3000), 5000)

    assert judged.failures == ["표본 부족: test query 3000 < 5000"]
    assert exit_code([judged]) == INSUFFICIENT_SAMPLE


def test_small_latest_patch_is_skipped_not_failed():
    """새 패치 직후에는 최신 패치 표본이 적다. 나빠 보여도 막지 않고 건너뛴 이유를 남긴다."""
    judged = judge_latest_patch(
        comparison({"ndcg@5": 0.5, "hit_rate@1": 0.1}, queries=400, name="최신", win_rate=dr(-0.05)), 1000)

    assert judged.failures == []
    assert judged.skipped == "query 400 < 1000라 판정에서 뺐다"


def test_large_enough_latest_patch_is_judged_with_the_same_criteria():
    judged = judge_latest_patch(
        comparison({"ndcg@5": 0.83, "hit_rate@1": 0.53}, queries=1500, name="최신", win_rate=dr(-0.01)), 1000)

    assert len(judged.failures) == 1
    assert exit_code([judge_total(comparison(CURRENT), 5000), judged]) == REJECTED


def test_latest_patch_compares_versions_numerically():
    assert latest_patch(["16.9", "16.18", "16.2"]) == "16.18"


def test_summary_states_verdict_metrics_and_skip():
    total = judge_total(comparison(CURRENT), 5000)
    latest = judge_latest_patch(comparison(CURRENT, queries=10, name="최신 패치 16.22", win_rate=None), 1000)

    summary = render_summary("ltr-261121", "ltr-261020", [total, latest], PASSED)

    assert summary.splitlines()[0] == "모델 비교: ltr-261121 vs 현재 ltr-261020 → *통과*"
    assert "DR 승률 0.5210 (현재 0.5200), 차이 +0.10%p (95% -0.90%p ~ +1.10%p)" in summary
    assert "NDCG@5 0.8300 (현재 0.8300), HitRate@1 0.5300 (현재 0.5300)" in summary
    assert "건너뜀: query 10 < 1000" in summary
    assert "DR 승률: 없음" in summary


def test_compare_judges_with_dr_win_rate_and_hit_rate_at_one():
    """후보는 정답을 1위로, 현재는 오답을 1위로 올린다. 4판 중 3판을 이긴다."""
    queries = 20
    dataset = Dataset(
        features=np.tile([[0.0], [1.0]], (queries, 1)), labels=np.tile([3, 0], queries),
        group=np.full(queries, 2), qids=[f"q{q}" for q in range(queries)], feature_names=["f"],
        schema_fingerprint="abc")
    meta = QueryMeta(
        qids=dataset.qids, match_ids=[f"KR_{q}" for q in range(queries)],
        patches=["16.22"] * 5 + ["16.21"] * 15, win=np.tile([1.0, 1.0, 1.0, 0.0], queries // 4),
        purchase_minute=np.full(queries, np.nan), team_gold_diff=np.full(queries, np.nan),
        own_total_gold=np.full(queries, np.nan))
    current_scores = np.tile([0.0, 1.0], queries)
    candidate_scores = np.tile([1.0, 0.0], queries)
    outcome = np.tile([0.6, 0.4], queries)

    total, latest = compare(dataset, meta, "16.22", current_scores, candidate_scores, np.full(queries, 0.5),
                            outcome, min_total_queries=10, min_latest_patch_queries=3)

    assert (total.queries, latest.queries) == (20, 5)
    assert latest.name == "game test 중 최신 패치 16.22"
    assert total.candidate["hit_rate@1"] == 1.0 and total.current["hit_rate@1"] == 0.0
    # 현재: 1순위가 실제 구매와 달라 Q만 남는다(0.4). 후보: 0.6 + (r − 0.6) / 0.5 → 이기면 1.4, 지면 −0.6
    assert total.dr["current"]["value"] == pytest.approx(0.4)
    assert total.dr["candidate"]["value"] == pytest.approx(0.9)
    assert total.dr["difference"]["difference"] == pytest.approx(0.5)
    assert total.failures == [] and latest.failures == []


def test_compare_without_outcome_model_reports_no_dr_win_rate():
    queries = 2
    dataset = Dataset(
        features=np.zeros((4, 1)), labels=np.array([3, 0, 3, 0]), group=np.full(queries, 2), qids=["q0", "q1"],
        feature_names=["f"], schema_fingerprint="abc")
    meta = QueryMeta(
        qids=dataset.qids, match_ids=["KR_0", "KR_1"], patches=["16.22", "16.22"], win=np.array([1.0, 0.0]),
        purchase_minute=np.full(queries, np.nan), team_gold_diff=np.full(queries, np.nan),
        own_total_gold=np.full(queries, np.nan))
    scores = np.array([1.0, 0.0, 1.0, 0.0])

    total, _ = compare(dataset, meta, "16.22", scores, scores, np.full(queries, 0.5), None,
                       min_total_queries=1, min_latest_patch_queries=1)

    assert total.dr is None
    assert exit_code([total]) == INSUFFICIENT_SAMPLE


def baseline_dataset():
    return Dataset(
        features=np.array([[1.0, 9.0], [0.0, 9.0]]), labels=np.array([3, 0]), group=np.array([2]),
        qids=["q0"], feature_names=["a", "b"], schema_fingerprint="full",
        baseline_feature_count=1, baseline_schema_fingerprint="base")


def one_split_model(fingerprint, feature):
    """feature가 0.5보다 크면 1, 아니면 0을 내는 나무 하나."""
    tree = {"split_feature": [feature], "threshold": [0.5], "default_left": [True], "left": [-1], "right": [-2],
            "leaf_value": [0.0, 1.0]}
    return {"schema_fingerprint": fingerprint, "trees": [tree]}


def test_baseline_model_is_scored_on_baseline_columns():
    scores = scores_for(one_split_model("base", 0), baseline_dataset())

    assert list(scores) == [1.0, 0.0]


def test_model_with_unknown_fingerprint_is_refused():
    with pytest.raises(ValueError):
        scores_for(one_split_model("other", 0), baseline_dataset())
