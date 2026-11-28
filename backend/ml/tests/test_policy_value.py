"""승률 지표 V 검증.

참값을 아는 생성 과정에서 V가 참값 근처로 나오는지, 승패를 섞으면 관측 승률로 돌아가는지를 본다.
"""
import numpy as np
import pytest

from dfgg_ltr.metrics import (
    hit_rate_by_outcome,
    policy_value,
    policy_value_difference,
    top1_matches,
)


def logged_data(seed, queries=20_000, candidates=5):
    """행동 확률 p로 실제 구매를 뽑고, 산 아이템의 승률 q로 승패를 뽑는다. p와 q를 모두 안다."""
    rng = np.random.default_rng(seed)
    logits = rng.normal(0, 1, (queries, candidates))
    behavior = np.exp(logits) / np.exp(logits).sum(axis=1, keepdims=True)
    quality = rng.uniform(0.3, 0.7, (queries, candidates))
    chosen = np.array([rng.choice(candidates, p=row) for row in behavior])
    win = (rng.random(queries) < quality[np.arange(queries), chosen]).astype(float)
    propensity = behavior[np.arange(queries), chosen]
    match_ids = [f"KR_{q // 3}" for q in range(queries)]
    return rng, behavior, quality, chosen, win, propensity, match_ids


def test_value_recovers_true_win_rate_of_a_deterministic_policy():
    """'승률이 가장 높은 아이템'을 고르는 정책의 참 승률을 IPS로 되찾는다."""
    _, _, quality, chosen, win, propensity, match_ids = logged_data(seed=0)
    policy = quality.argmax(axis=1)

    result = policy_value(policy == chosen, propensity, win, match_ids, p_min=0.0, rounds=200)

    true_value = quality.max(axis=1).mean()
    assert result.value == pytest.approx(true_value, abs=0.02)
    assert result.lower < true_value < result.upper


def test_shuffled_wins_bring_value_back_to_observed_win_rate():
    """placebo: 승패를 섞으면 어떤 정책이든 V ≈ 관측 승률이다."""
    rng, _, quality, chosen, win, propensity, match_ids = logged_data(seed=1)
    shuffled = rng.permutation(win)

    result = policy_value(quality.argmax(axis=1) == chosen, propensity, shuffled, match_ids, rounds=200)

    assert result.value == pytest.approx(result.observed_win, abs=0.02)


def test_value_ess_and_clipping_follow_the_definition():
    matches = np.array([True, True, False, True])
    propensity = np.array([0.5, 0.25, 0.9, 0.01])
    win = np.array([1.0, 0.0, 1.0, 1.0])

    result = policy_value(matches, propensity, win, ["A", "B", "C", "D"], p_min=0.02, rounds=50)

    weights = np.array([2.0, 4.0, 50.0])
    assert result.value == pytest.approx((2.0 + 50.0) / weights.sum())
    assert result.ess == pytest.approx(weights.sum() ** 2 / (weights ** 2).sum())
    assert result.matched == 3
    assert result.clipped_share == pytest.approx(1 / 3)
    assert result.observed_win == pytest.approx(0.75)


def test_unknown_propensity_or_win_is_skipped_not_counted_as_zero():
    matches = np.array([True, True, True])
    propensity = np.array([0.5, np.nan, 0.5])
    win = np.array([1.0, 0.0, np.nan])

    result = policy_value(matches, propensity, win, ["A", "B", "C"], rounds=50)

    assert result.value == 1.0
    assert result.usable == 1
    assert result.coverage == pytest.approx(1 / 3)


def test_no_matched_query_gives_nan_value_and_zero_ess():
    result = policy_value(np.array([False, False]), np.array([0.5, 0.5]), np.array([1.0, 0.0]), ["A", "B"],
                          rounds=50)

    assert np.isnan(result.value)
    assert np.isnan(result.lower) and np.isnan(result.upper)
    assert result.ess == 0.0


def test_difference_of_identical_policies_is_exactly_zero():
    _, _, quality, chosen, win, propensity, match_ids = logged_data(seed=2, queries=3000)
    matches = quality.argmax(axis=1) == chosen

    result = policy_value_difference(matches, matches, propensity, win, match_ids, rounds=100)

    assert result == {"difference": 0.0, "lower": 0.0, "upper": 0.0}


def test_difference_detects_a_better_policy():
    _, _, quality, chosen, win, propensity, match_ids = logged_data(seed=3)

    result = policy_value_difference(quality.argmax(axis=1) == chosen, quality.argmin(axis=1) == chosen,
                                     propensity, win, match_ids, rounds=200)

    assert result["lower"] > 0
    assert result["lower"] <= result["difference"] <= result["upper"]


def test_top1_matches_checks_whether_the_highest_score_is_the_purchase():
    labels = np.array([0, 3, 2, 3, 0])
    scores = np.array([0.1, 0.9, 0.5, 0.2, 0.8])

    assert top1_matches(labels, scores, np.array([3, 2])).tolist() == [True, False]


def test_hit_rate_by_outcome_splits_won_and_lost_games():
    labels = np.array([3, 0, 0, 3, 3, 0])
    scores = np.array([1.0, 0.0, 1.0, 0.0, 1.0, 0.0])
    group = np.array([2, 2, 2])
    win = np.array([1.0, 0.0, 0.0])

    result = hit_rate_by_outcome(labels, scores, group, win, k=1)

    assert result == {"hit_rate@1_win": 1.0, "hit_rate@1_loss": 0.5, "win_lift": 0.5}
