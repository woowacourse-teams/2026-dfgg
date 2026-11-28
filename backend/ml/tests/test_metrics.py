"""랭킹 지표. NDCG만으로는 "정답을 몇 위에 뒀는가"가 안 보여 MRR·HitRate를 함께 본다."""
import numpy as np
import pytest

from dfgg_ltr.metrics import evaluate_ranking, hit_rate_at_k, mrr, ndcg_at_k


class TestNdcg:
    def test_perfect_ranking_scores_one(self):
        assert ndcg_at_k(np.array([3, 2, 1, 0]), np.array([4.0, 3.0, 2.0, 1.0]), 5) == pytest.approx(1.0)

    def test_reversed_ranking_scores_less_than_one(self):
        assert ndcg_at_k(np.array([3, 2, 1, 0]), np.array([1.0, 2.0, 3.0, 4.0]), 5) < 1.0

    def test_only_the_top_k_positions_count(self):
        """정답을 6위에 두면 NDCG@5는 0이다."""
        labels = np.array([0, 0, 0, 0, 0, 3])
        scores = np.array([6.0, 5.0, 4.0, 3.0, 2.0, 1.0])

        assert ndcg_at_k(labels, scores, 5) == 0.0

    def test_a_query_with_no_relevant_item_is_undefined_not_zero(self):
        """관련 아이템이 없으면 이상적 DCG가 0이라 나눌 수 없다. 0점으로 세면 평균이 왜곡된다."""
        assert ndcg_at_k(np.array([0, 0, 0]), np.array([3.0, 2.0, 1.0]), 5) is None


class TestMrr:
    def test_ground_truth_first_gives_one(self):
        assert mrr(np.array([3, 0, 0]), np.array([9.0, 2.0, 1.0])) == pytest.approx(1.0)

    def test_ground_truth_third_gives_one_third(self):
        assert mrr(np.array([0, 0, 3]), np.array([9.0, 8.0, 1.0])) == pytest.approx(1 / 3)

    def test_only_grade_three_counts_as_the_answer(self):
        """등급 2(같은 게임의 나중 구매)는 정답이 아니다. 정답은 '바로 다음 구매' 하나뿐이다."""
        assert mrr(np.array([2, 3, 0]), np.array([9.0, 8.0, 1.0])) == pytest.approx(0.5)

    def test_returns_none_when_there_is_no_ground_truth(self):
        assert mrr(np.array([0, 1, 2]), np.array([3.0, 2.0, 1.0])) is None


class TestHitRate:
    def test_counts_a_hit_when_ground_truth_is_within_k(self):
        assert hit_rate_at_k(np.array([0, 3, 0]), np.array([9.0, 8.0, 1.0]), 5) == 1.0

    def test_counts_a_miss_when_ground_truth_falls_outside_k(self):
        labels = np.array([0, 0, 0, 0, 0, 3])
        scores = np.array([6.0, 5.0, 4.0, 3.0, 2.0, 1.0])

        assert hit_rate_at_k(labels, scores, 5) == 0.0


class TestEvaluateRanking:
    """query별로 잘라서 평균낸다 — 전체를 한 덩어리로 정렬하면 지표가 의미를 잃는다."""

    def test_averages_over_queries_using_group_boundaries(self):
        labels = np.array([3, 0, 0, 3])   # query1: 3개, query2: 1개
        scores = np.array([9.0, 1.0, 0.0, 5.0])

        result = evaluate_ranking(labels, scores, group=[3, 1])

        assert result["ndcg@1"] == pytest.approx(1.0)
        assert result["mrr"] == pytest.approx(1.0)
        assert result["hit_rate@5"] == pytest.approx(1.0)

    def test_a_query_ranked_badly_pulls_the_average_down(self):
        labels = np.array([3, 0, 0, 3])
        scores = np.array([0.0, 9.0, 1.0, 5.0])  # query1은 정답이 3위

        result = evaluate_ranking(labels, scores, group=[3, 1])

        assert result["mrr"] == pytest.approx((1 / 3 + 1.0) / 2)

    def test_reports_how_many_queries_it_scored(self):
        result = evaluate_ranking(np.array([3, 0, 3]), np.array([1.0, 0.0, 1.0]), group=[2, 1])

        assert result["queries"] == 2

    def test_skips_queries_that_have_no_ground_truth_instead_of_scoring_them_zero(self):
        labels = np.array([3, 0, 0, 0])   # query2에는 정답이 없다
        scores = np.array([9.0, 1.0, 5.0, 4.0])

        result = evaluate_ranking(labels, scores, group=[2, 2])

        assert result["queries"] == 1
        assert result["mrr"] == pytest.approx(1.0)

    def test_group_lengths_must_cover_every_row(self):
        with pytest.raises(ValueError):
            evaluate_ranking(np.array([3, 0, 0]), np.array([1.0, 0.0, 0.0]), group=[2])
