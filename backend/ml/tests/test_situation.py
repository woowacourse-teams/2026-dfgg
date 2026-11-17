"""게임 상황 값이 순위를 "상황에 맞게" 바꾸는지 보는 도구 검증.

상황 값은 한 요청의 모든 후보가 같은 값을 갖는다. 그래서 가짜 후보를 만들어 묻는 기존 시나리오 도구와 달리,
실제 후보 묶음에서 그 값만 바꿔 다시 점수를 매긴다.
"""
import json

import numpy as np
import pytest

from dfgg_ltr.dataset import Dataset
from dfgg_ltr.situation import (
    best_rank_of,
    empty_value_rates,
    items_with_tag,
    load_item_catalog,
    low_and_high_values,
    query_permutation_importance,
    query_values,
    substitute,
    substitution_effect,
    top1_share,
)

MAGIC_RESIST = 3111
OTHER = 3031

# 열: [기본 점수, 마법 저항 아이템인가, 상대 주문력 비중(query 수준)]
BASE, IS_MAGIC_RESIST, ENEMY_AP_SHARE = 0, 1, 2


def predict(features):
    """상대 주문력 비중이 높을수록 마법 저항 아이템의 점수를 올리는 가짜 모델."""
    return features[:, BASE] + features[:, IS_MAGIC_RESIST] * features[:, ENEMY_AP_SHARE]


def dataset_of(queries):
    """queries: [(상대 주문력 비중, 구매 단계)] — query마다 후보 둘(마법 저항 0.4점, 다른 아이템 0.6점)."""
    features, item_ids, steps = [], [], []
    for share, step in queries:
        features += [[0.4, 1.0, share], [0.6, 0.0, share]]
        item_ids += [MAGIC_RESIST, OTHER]
        steps.append(step)
    return Dataset(
        features=np.array(features, dtype=float), labels=np.array([3, 0] * len(queries)),
        group=np.array([2] * len(queries)), qids=[f"q{i}" for i in range(len(queries))],
        feature_names=["base", "is_magic_resist", "enemy_spell_damage_gold_share"],
        schema_fingerprint="abc", item_ids=np.array(item_ids), purchase_steps=np.array(steps),
    )


class TestItemCatalog:
    def test_reads_tags_per_item_and_selects_items_by_tag(self, tmp_path):
        """아이템은 번호 목록이 아니라 Java가 내보낸 태그로 고른다."""
        path = tmp_path / "item_catalog.json"
        path.write_text(json.dumps({"items": [
            {"item_id": 3111, "name": {"en_US": "Mercury's Treads"}, "tags": ["SpellBlock"], "total_gold": 1250},
            {"item_id": 3031, "name": {"en_US": "Infinity Edge"}, "tags": ["Damage"], "total_gold": 3450},
        ]}), encoding="utf-8")

        catalog = load_item_catalog(path)

        assert items_with_tag(catalog, "SpellBlock") == {3111}
        assert items_with_tag(catalog, "NoSuchTag") == set()


class TestQueryValues:
    def test_takes_one_value_per_query(self):
        dataset = dataset_of([(0.2, 0), (0.8, 1)])

        np.testing.assert_array_equal(
            query_values(dataset.features, dataset.group, ENEMY_AP_SHARE), np.array([0.2, 0.8]))


class TestSubstitute:
    def test_sets_the_same_value_on_every_candidate_of_the_selected_queries(self):
        """한 요청의 모든 후보에 같은 값을 넣는다 — 실제 서빙에서 그 값은 요청마다 하나다."""
        dataset = dataset_of([(0.2, 0), (0.8, 1)])

        changed = substitute(dataset.features, dataset.group, ENEMY_AP_SHARE, 0.5, np.array([True, False]))

        np.testing.assert_array_equal(changed[:, ENEMY_AP_SHARE], np.array([0.5, 0.5, 0.8, 0.8]))

    def test_leaves_the_original_matrix_untouched(self):
        dataset = dataset_of([(0.2, 0)])

        substitute(dataset.features, dataset.group, ENEMY_AP_SHARE, 0.9, np.array([True]))

        np.testing.assert_array_equal(dataset.features[:, ENEMY_AP_SHARE], np.array([0.2, 0.2]))


    def test_aligns_queries_of_different_sizes_with_their_rows(self):
        """query마다 후보 수가 달라도 고른 query의 행만 바뀐다 — query 표시와 행이 어긋나면 엉뚱한 요청이 바뀐다."""
        features = np.zeros((6, 3))
        group = [1, 3, 2]

        changed = substitute(features, group, ENEMY_AP_SHARE, 0.7, np.array([False, True, False]))

        np.testing.assert_array_equal(changed[:, ENEMY_AP_SHARE], np.array([0.0, 0.7, 0.7, 0.7, 0.0, 0.0]))


class TestLowAndHighValues:
    def test_takes_the_percentiles_of_the_selected_queries_only(self):
        dataset = dataset_of([(share, 0) for share in np.linspace(0.0, 1.0, 11)] + [(50.0, 0)])
        selected = np.array([True] * 11 + [False])

        low, high = low_and_high_values(dataset, ENEMY_AP_SHARE, selected)

        assert (low, high) == (pytest.approx(0.1), pytest.approx(0.9))


class TestQueryPermutationImportance:
    def test_a_value_that_cannot_change_the_order_within_a_query_has_no_importance(self):
        """query 수준 값은 한 요청의 모든 후보가 같다. 점수를 똑같이 올리기만 하면 순위는 그대로다.

        행 단위로 섞으면 같은 요청의 후보마다 다른 값이 들어가 순위가 흔들리고, 없는 중요도가 생긴다.
        """
        dataset = dataset_of([(share, 0) for share in np.linspace(0.0, 1.0, 40)])
        shifts_every_candidate_equally = lambda features: features[:, BASE] + 5 * features[:, ENEMY_AP_SHARE]

        importance = query_permutation_importance(
            shifts_every_candidate_equally, dataset, {"enemy_ap": [ENEMY_AP_SHARE]})

        assert importance["enemy_ap"] == 0.0

    def test_a_value_that_decides_the_order_loses_ndcg_when_shuffled_across_queries(self):
        """값이 순위를 정하면, 다른 요청의 값과 바꿨을 때 지표가 떨어진다."""
        # 정답은 마법 저항 아이템이고, 상대 주문력 비중이 0.2를 넘어야 1순위가 된다. 절반은 높고 절반은 낮다.
        dataset = dataset_of([(0.9, 0)] * 20 + [(0.0, 0)] * 20)
        dataset.labels[:] = np.where(np.arange(80) < 40, [3, 0] * 40, [0, 3] * 40)

        importance = query_permutation_importance(predict, dataset, {"enemy_ap": [ENEMY_AP_SHARE]})

        assert importance["enemy_ap"] > 0.0

    def test_leaves_the_original_matrix_untouched(self):
        dataset = dataset_of([(share, 0) for share in np.linspace(0.0, 1.0, 10)])
        before = dataset.features.copy()

        query_permutation_importance(predict, dataset, {"enemy_ap": [ENEMY_AP_SHARE]})

        np.testing.assert_array_equal(dataset.features, before)


class TestBestRankOf:
    def test_gives_the_best_rank_of_the_target_items_in_each_query(self):
        scores = np.array([0.9, 0.1, 0.2, 0.8])
        item_ids = np.array([MAGIC_RESIST, OTHER, MAGIC_RESIST, OTHER])

        np.testing.assert_array_equal(
            best_rank_of(scores, [2, 2], item_ids, {MAGIC_RESIST}), np.array([1.0, 2.0]))

    def test_is_missing_when_the_query_has_no_target_candidate(self):
        """대상 아이템이 후보에 없는 query는 순위를 말할 수 없다 — 꼴찌로 세지 않는다."""
        ranks = best_rank_of(np.array([0.9, 0.1]), [2], np.array([OTHER, OTHER]), {MAGIC_RESIST})

        assert np.isnan(ranks[0])


class TestTop1Share:
    def test_is_the_share_of_selected_queries_whose_first_item_is_a_target(self):
        scores = np.array([0.9, 0.1, 0.2, 0.8, 0.9, 0.1])
        item_ids = np.array([MAGIC_RESIST, OTHER] * 3)

        share = top1_share(scores, [2, 2, 2], item_ids, {MAGIC_RESIST}, np.array([True, True, False]))

        assert share == 0.5

    def test_rejects_a_query_selection_of_the_wrong_length(self):
        """query 표시가 짧으면 뒤의 query가 조용히 빠진다 — 멈추고 알린다."""
        with pytest.raises(ValueError):
            top1_share(np.array([0.9, 0.1, 0.2, 0.8]), [2, 2], np.array([MAGIC_RESIST, OTHER] * 2),
                       {MAGIC_RESIST}, np.array([True]))


class TestSubstitutionEffect:
    def test_reports_how_the_target_items_move_between_the_low_and_high_value(self):
        """상대 주문력 비중을 낮음→높음으로 바꾸면 가짜 모델에서 마법 저항 아이템이 1순위로 올라온다."""
        dataset = dataset_of([(share, 0) for share in np.linspace(0.0, 1.0, 11)])

        effect = substitution_effect(
            predict, dataset, ENEMY_AP_SHARE, {MAGIC_RESIST}, np.ones(11, dtype=bool))

        # 하위 10%·상위 10% 값
        assert effect["low_value"] == pytest.approx(0.1)
        assert effect["high_value"] == pytest.approx(0.9)
        assert effect["queries"] == 11
        # 0.4 + 0.1 < 0.6 이라 낮을 때는 2순위, 0.4 + 0.9 > 0.6 이라 높을 때는 1순위
        assert effect["mean_rank_low"] == 2.0
        assert effect["mean_rank_high"] == 1.0
        assert effect["top1_share_low"] == 0.0
        assert effect["top1_share_high"] == 1.0

    def test_only_uses_the_selected_queries(self):
        """상황을 모르는 query는 뺀다 — 값을 바꿔 넣을 근거가 없다."""
        dataset = dataset_of([(0.0, 0), (1.0, 0), (np.nan, 0)])

        seen = []

        def recording_predict(features):
            seen.append(features.copy())
            return predict(features)

        effect = substitution_effect(
            recording_predict, dataset, ENEMY_AP_SHARE, {MAGIC_RESIST}, np.array([True, True, False]))

        assert effect["queries"] == 2
        # 고르지 않은 query의 값은 바뀌지 않는다
        for features in seen:
            assert np.isnan(features[4:, ENEMY_AP_SHARE]).all()

    def test_skips_queries_without_a_target_candidate(self):
        dataset = dataset_of([(0.0, 0), (1.0, 0)])
        dataset.item_ids[:2] = OTHER  # 첫 query에는 마법 저항 아이템 후보가 없다

        effect = substitution_effect(
            predict, dataset, ENEMY_AP_SHARE, {MAGIC_RESIST}, np.ones(2, dtype=bool))

        assert effect["queries"] == 1


class TestEmptyValueRates:
    def test_counts_missing_and_zero_values_per_purchase_step(self):
        """첫 번째·두 번째 Core를 고를 때 아이템 값이 얼마나 비는지 — Core만 봐서 놓치는 양이다."""
        dataset = dataset_of([(np.nan, 0), (0.0, 0), (0.5, 0), (0.5, 0), (0.0, 1), (0.7, 1), (0.9, 5)])

        rates = empty_value_rates(dataset, [ENEMY_AP_SHARE], steps=(0, 1))

        assert rates == [
            {"purchase_step": 0, "feature": "enemy_spell_damage_gold_share",
             "queries": 4, "missing": 0.25, "zero": 0.25},
            {"purchase_step": 1, "feature": "enemy_spell_damage_gold_share",
             "queries": 2, "missing": 0.0, "zero": 0.5},
        ]

    def test_can_be_limited_to_queries_that_know_the_situation(self):
        """상황을 모르는 query는 뺀다 — 그 값이 빈 것은 Core만 봐서가 아니라 상황 자체를 몰라서다."""
        dataset = dataset_of([(np.nan, 0), (np.nan, 0), (0.5, 0)])

        rates = empty_value_rates(
            dataset, [ENEMY_AP_SHARE], steps=(0,), queries=np.array([False, True, True]))

        assert rates == [{"purchase_step": 0, "feature": "enemy_spell_damage_gold_share",
                          "queries": 2, "missing": 0.5, "zero": 0.0}]
