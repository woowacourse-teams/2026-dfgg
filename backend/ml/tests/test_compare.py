"""두 모델이 같은 스냅샷에서 얼마나 다른 순위를 내는지 잰다.

지표(NDCG 등)는 "정답을 얼마나 위에 올렸나"만 말한다. 두 모델의 지표가 같아도 추천은 크게 다를 수 있고,
그 반대도 있다. 그래서 "1순위가 바뀐 비율"을 따로 잰다. 같은 데이터로 씨앗만 바꿔 다시 학습해도 순위는
조금 바뀌므로, 그 폭(잡음 바닥)과 나란히 놓아야 읽을 수 있다.
"""
import numpy as np
import pytest

from dfgg_ltr.compare import rank_change, top_items


def test_top_items_orders_by_score_within_each_query():
    """query마다 점수가 높은 순으로 아이템 번호를 낸다."""
    scores = np.array([0.1, 0.9, 0.5, 0.3, 0.7])
    item_ids = np.array([10, 20, 30, 40, 50])

    assert top_items(scores, [3, 2], item_ids, k=2) == [(20, 30), (50, 40)]


def test_top_items_breaks_ties_by_item_id():
    """점수가 같으면 아이템 번호가 작은 쪽이 앞이다 — Java 랭커의 동점 규칙과 같다."""
    scores = np.array([0.5, 0.5, 0.5])
    item_ids = np.array([30, 10, 20])

    assert top_items(scores, [3], item_ids, k=3) == [(10, 20, 30)]


def test_top_items_returns_every_item_when_the_query_is_smaller_than_k():
    scores = np.array([0.2, 0.8])
    item_ids = np.array([10, 20])

    assert top_items(scores, [2], item_ids, k=3) == [(20, 10)]


def test_identical_scores_change_nothing():
    scores = np.array([0.1, 0.9, 0.5, 0.3, 0.7, 0.2])
    item_ids = np.array([10, 20, 30, 40, 50, 60])

    change = rank_change(scores, scores.copy(), [3, 3], item_ids)

    assert change == {"top1_changed": 0.0, "top3_changed": 0.0, "queries": 2}


def test_top1_change_is_the_share_of_queries_whose_first_item_differs():
    """query 둘 중 하나에서만 1순위가 바뀌면 0.5다."""
    item_ids = np.array([10, 20, 30, 40])
    first = np.array([0.9, 0.1, 0.9, 0.1])
    second = np.array([0.9, 0.1, 0.1, 0.9])

    change = rank_change(first, second, [2, 2], item_ids)

    assert change["top1_changed"] == 0.5
    assert change["queries"] == 2


def test_top3_change_compares_the_set_not_the_order():
    """상위 3개의 구성이 같으면 순서가 달라도 바뀐 것으로 세지 않는다."""
    item_ids = np.array([10, 20, 30, 40])
    first = np.array([0.9, 0.8, 0.7, 0.1])
    second = np.array([0.7, 0.8, 0.9, 0.1])

    change = rank_change(first, second, [4], item_ids)

    assert change["top1_changed"] == 1.0
    assert change["top3_changed"] == 0.0


def test_top3_change_counts_a_query_when_an_item_enters_the_top_three():
    item_ids = np.array([10, 20, 30, 40])
    first = np.array([0.9, 0.8, 0.7, 0.1])
    second = np.array([0.9, 0.8, 0.1, 0.7])

    change = rank_change(first, second, [4], item_ids)

    assert change["top1_changed"] == 0.0
    assert change["top3_changed"] == 1.0


def test_rejects_scores_that_do_not_cover_the_groups():
    """행 수가 묶음과 맞지 않으면 다른 query의 후보끼리 비교하게 된다."""
    with pytest.raises(ValueError):
        rank_change(np.array([0.1, 0.2]), np.array([0.1, 0.2]), [3], np.array([10, 20]))


def test_can_be_limited_to_selected_queries():
    """일부 query만 골라 잴 수 있다 — 맞라인에서 앞선 판과 뒤처진 판을 따로 볼 때 쓴다."""
    item_ids = np.array([10, 20, 30, 40])
    first = np.array([0.9, 0.1, 0.9, 0.1])
    second = np.array([0.9, 0.1, 0.1, 0.9])

    change = rank_change(first, second, [2, 2], item_ids, queries=np.array([True, False]))

    assert change == {"top1_changed": 0.0, "top3_changed": 0.0, "queries": 1}


def test_rejects_a_query_selection_of_the_wrong_length():
    """query 표시가 짧으면 뒤의 query가 조용히 빠져 비율이 틀어진다 — 멈추고 알린다."""
    scores = np.array([0.9, 0.1, 0.9, 0.1])

    with pytest.raises(ValueError):
        rank_change(scores, scores, [2, 2], np.array([10, 20, 30, 40]), queries=np.array([True]))
