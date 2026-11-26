import numpy as np
import pytest

from dfgg_ltr.metrics import doubly_robust_difference, doubly_robust_terms, doubly_robust_value, top1_values


def test_top1_values_picks_the_value_of_the_highest_scored_row_in_each_query():
    values = np.array([0.1, 0.2, 0.3, 0.4, 0.5])
    scores = np.array([0.0, 9.0, 5.0, 1.0, 2.0])

    assert list(top1_values(values, scores, np.array([2, 3]))) == [0.2, 0.3]


def test_unmatched_query_keeps_only_the_direct_estimate():
    terms = doubly_robust_terms(np.array([False]), np.array([0.4]), np.array([0.5]), np.array([1.0]))

    assert terms[0] == pytest.approx(0.4)


def test_matched_query_corrects_the_direct_estimate_by_inverse_propensity():
    terms = doubly_robust_terms(np.array([True, True]), np.array([0.6, 0.6]), np.array([0.5, 0.5]),
                                np.array([1.0, 0.0]))

    assert list(terms) == pytest.approx([0.6 + 0.4 / 0.5, 0.6 - 0.6 / 0.5])


def test_small_propensity_is_clipped_to_the_floor():
    terms = doubly_robust_terms(np.array([True]), np.array([0.5]), np.array([0.001]), np.array([1.0]))

    assert terms[0] == pytest.approx(0.5 + 0.5 / 0.02)


def test_query_without_propensity_or_outcome_is_left_out():
    terms = doubly_robust_terms(np.array([True, True]), np.array([0.5, 0.5]), np.array([np.nan, 0.5]),
                                np.array([1.0, np.nan]))

    assert np.isnan(terms).all()


def test_value_is_the_mean_of_known_terms_with_an_interval_around_it():
    terms = np.array([0.2, 0.8, np.nan, 0.5, 0.5])

    value = doubly_robust_value(terms, ["KR_1", "KR_2", "KR_3", "KR_4", "KR_5"], rounds=200)

    assert value["value"] == pytest.approx(0.5)
    assert value["usable"] == 4
    assert value["lower"] <= 0.5 <= value["upper"]


def test_difference_is_paired_by_query():
    """두 모델이 같은 query에서 같이 흔들리면 차이의 구간은 0으로 좁아진다."""
    rng = np.random.default_rng(0)
    baseline = rng.normal(0.5, 0.3, 400)
    candidate = baseline + 0.01

    difference = doubly_robust_difference(candidate, baseline, [f"KR_{q}" for q in range(400)], rounds=200)

    assert difference["difference"] == pytest.approx(0.01)
    assert difference["upper"] - difference["lower"] == pytest.approx(0.0, abs=1e-9)
