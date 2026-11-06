"""counter-only 후보가 최종 순위에서 어떻게 되는지 본다.

이번 작업이 고치려던 결함이 바로 여기다. 구조상 counter는 "내 챔피언 + 적 + 아이템" 삼중항을
쓰므로 야스오에게 존야가 올라오면 안 된다. 다만 **AD/AP hard filter는 두지 않기로** 했으므로,
off-meta 빌드를 막는 게 아니라 LTR이 균형을 학습했는지를 관측치로 확인한다.
"""
import numpy as np
import pytest

from dfgg_ltr.counter_analysis import (
    SOURCE_FEATURES,
    analyze_sources,
    source_combination,
)


def features_with_sources(build=0, self_syn=0, ally=0, counter=0, width=8):
    row = [0.0] * width
    row[0], row[1], row[2], row[3] = build, self_syn, ally, counter
    return row


class TestSourceCombination:
    def test_names_the_single_generator_that_found_it(self):
        assert source_combination(features_with_sources(counter=1)) == "COUNTER"

    def test_joins_multiple_generators_in_declaration_order(self):
        combination = source_combination(features_with_sources(build=1, counter=1))

        assert combination == "BUILD+COUNTER"

    def test_treats_missing_source_flags_as_not_found(self):
        """결측은 '못 찾았다'와 다르지만, source 플래그는 generator가 항상 채운다."""
        row = features_with_sources(build=1)
        row[3] = float("nan")

        assert source_combination(row) == "BUILD"

    def test_labels_a_candidate_no_generator_claims(self):
        assert source_combination(features_with_sources()) == "NONE"

    def test_source_features_are_the_first_four_columns(self):
        assert SOURCE_FEATURES == ["source_build", "source_self_synergy",
                                   "source_ally_synergy", "source_counter"]


class TestAnalyzeSources:
    """query 하나에 후보 3개, 두 query."""

    def _dataset(self):
        features = np.array([
            features_with_sources(build=1),               # q1: 1위
            features_with_sources(counter=1),             # q1: 2위
            features_with_sources(build=1, counter=1),    # q1: 3위
            features_with_sources(counter=1),             # q2: 1위
            features_with_sources(build=1),               # q2: 2위
        ], dtype=float)
        labels = np.array([3, 0, 0, 0, 3])
        group = [3, 2]
        scores = np.array([9.0, 5.0, 1.0, 9.0, 5.0])
        return features, labels, group, scores

    def test_counts_candidates_per_source_combination(self):
        features, labels, group, scores = self._dataset()

        result = analyze_sources(features, scores, group, top_k=5)

        assert result["COUNTER"]["candidates"] == 2
        assert result["BUILD+COUNTER"]["candidates"] == 1

    def test_reports_how_often_each_combination_reaches_top_k(self):
        features, labels, group, scores = self._dataset()

        # top_k=1이면 q1은 BUILD, q2는 COUNTER가 1위다
        result = analyze_sources(features, scores, group, top_k=1)

        assert result["BUILD"]["top_k_rate"] == pytest.approx(0.5)
        assert result["COUNTER"]["top_k_rate"] == pytest.approx(0.5)

    def test_reports_mean_final_rank_per_combination(self):
        features, labels, group, scores = self._dataset()

        result = analyze_sources(features, scores, group, top_k=5)

        # COUNTER 단독은 q1에서 2위, q2에서 1위 → 평균 1.5
        assert result["COUNTER"]["mean_rank"] == pytest.approx(1.5)

    def test_counts_how_often_a_combination_is_the_actual_next_purchase(self):
        """정답률이 낮은데 top-5를 많이 차지하면 그 경로가 자리를 낭비하고 있다는 뜻이다."""
        features, labels, group, scores = self._dataset()

        result = analyze_sources(features, scores, group, top_k=5, labels=labels)

        assert result["BUILD"]["ground_truth_rate"] == pytest.approx(1.0)
        assert result["COUNTER"]["ground_truth_rate"] == pytest.approx(0.0)

    def test_group_lengths_must_cover_every_row(self):
        features, labels, group, scores = self._dataset()

        with pytest.raises(ValueError):
            analyze_sources(features, scores, [3], top_k=5)


class TestItemType:
    """아이템을 AD/AP로 가른다. Data Dragon 태그 기준."""

    def test_spell_damage_only_is_ap(self):
        from dfgg_ltr.counter_analysis import item_type

        assert item_type(["SpellDamage", "ManaRegen"]) == "AP"

    def test_attack_damage_only_is_ad(self):
        from dfgg_ltr.counter_analysis import item_type

        assert item_type(["Damage", "CriticalStrike"]) == "AD"

    def test_carrying_both_is_neither(self):
        """둘 다 붙은 아이템으로 성향을 논하면 관측이 흐려진다."""
        from dfgg_ltr.counter_analysis import item_type

        assert item_type(["Damage", "SpellDamage"]) == "OTHER"

    def test_carrying_neither_is_neither(self):
        from dfgg_ltr.counter_analysis import item_type

        assert item_type(["Armor", "Health"]) == "OTHER"


class TestChampionApShare:
    """챔피언 성향을 태그가 아니라 **실제 구매 기록**으로 정한다.

    "야스오는 AD 챔피언"을 태그로 단정하면 논쟁이 되지만, 야스오의 실제 코어 구매 중 AP
    아이템이 몇 %인지는 데이터가 답한다.
    """

    def test_computes_the_ap_share_of_actual_purchases(self):
        from dfgg_ltr.counter_analysis import champion_ap_share

        champion_ids = [157, 157, 157, 157]
        item_ids = [1, 2, 3, 4]
        labels = np.array([3, 3, 3, 3])
        item_types = {1: "AD", 2: "AD", 3: "AD", 4: "AP"}

        share = champion_ap_share(champion_ids, item_ids, labels, item_types)

        assert share[157] == pytest.approx(0.25)

    def test_counts_only_actual_next_purchases_not_every_candidate(self):
        """후보로 올라온 것과 실제로 산 것은 다르다. 성향은 산 것으로만 정한다."""
        from dfgg_ltr.counter_analysis import champion_ap_share

        champion_ids = [157, 157]
        item_ids = [1, 4]
        labels = np.array([3, 0])   # 4번 AP는 후보였을 뿐 사지 않았다
        item_types = {1: "AD", 4: "AP"}

        share = champion_ap_share(champion_ids, item_ids, labels, item_types)

        assert share[157] == pytest.approx(0.0)

    def test_ignores_items_that_are_neither_ad_nor_ap(self):
        from dfgg_ltr.counter_analysis import champion_ap_share

        share = champion_ap_share([157, 157], [1, 9], np.array([3, 3]),
                                  {1: "AP", 9: "OTHER"})

        assert share[157] == pytest.approx(1.0)

    def test_a_champion_with_no_typed_purchase_is_absent_rather_than_zero(self):
        """0으로 두면 'AP를 전혀 안 사는 챔피언'과 구분되지 않는다."""
        from dfgg_ltr.counter_analysis import champion_ap_share

        share = champion_ap_share([157], [9], np.array([3]), {9: "OTHER"})

        assert 157 not in share


class TestOffTypeRecommendations:
    """이번 작업이 고치려던 결함의 최종 지표.

    구 구조는 "적 + 팀의 아무 아이템"이라 야스오(AD)에게 존야(AP)가 올라올 수 있었다.
    새 구조는 "내 챔피언 + 적 + 아이템"이다. **AD/AP hard filter는 두지 않기로** 했으므로
    0%를 목표로 하지 않는다 — off-meta 빌드는 남아야 한다. 관측치로 본다.
    """

    def _inputs(self):
        # query 하나, 후보 3개. 챔피언 157은 AD 성향(ap_share=0).
        features = np.array([
            features_with_sources(build=1),     # 1위: AD 아이템
            features_with_sources(counter=1),   # 2위: AP 아이템 (counter 단독)
            features_with_sources(build=1),     # 3위: AD 아이템
        ], dtype=float)
        scores = np.array([9.0, 5.0, 1.0])
        return features, scores, [3], [157, 157, 157], [1, 2, 3]

    def test_counts_recommendations_that_go_against_the_champion_s_type(self):
        from dfgg_ltr.counter_analysis import off_type_top_k

        features, scores, group, champion_ids, item_ids = self._inputs()

        result = off_type_top_k(
            features, scores, group, champion_ids, item_ids,
            item_types={1: "AD", 2: "AP", 3: "AD"},
            champion_ap_shares={157: 0.0}, top_k=5)

        assert result["off_type_slots"] == 1
        assert result["typed_slots"] == 3

    def test_reports_how_many_off_type_slots_came_from_counter_alone(self):
        """구 구조의 결함이 정확히 이 경로였다. 여기가 크면 결함이 남아 있다는 뜻이다."""
        from dfgg_ltr.counter_analysis import off_type_top_k

        features, scores, group, champion_ids, item_ids = self._inputs()

        result = off_type_top_k(
            features, scores, group, champion_ids, item_ids,
            item_types={1: "AD", 2: "AP", 3: "AD"},
            champion_ap_shares={157: 0.0}, top_k=5)

        assert result["off_type_from_counter_only"] == 1

    def test_ignores_champions_whose_type_is_unknown(self):
        from dfgg_ltr.counter_analysis import off_type_top_k

        features, scores, group, champion_ids, item_ids = self._inputs()

        result = off_type_top_k(
            features, scores, group, champion_ids, item_ids,
            item_types={1: "AD", 2: "AP", 3: "AD"},
            champion_ap_shares={}, top_k=5)

        assert result["typed_slots"] == 0

    def test_a_champion_that_genuinely_buys_both_is_not_counted_as_off_type(self):
        """AP 비중이 중간인 챔피언에게는 어느 쪽도 '어긋난' 추천이 아니다."""
        from dfgg_ltr.counter_analysis import off_type_top_k

        features, scores, group, champion_ids, item_ids = self._inputs()

        result = off_type_top_k(
            features, scores, group, champion_ids, item_ids,
            item_types={1: "AD", 2: "AP", 3: "AD"},
            champion_ap_shares={157: 0.5}, top_k=5)

        assert result["typed_slots"] == 0

    def test_only_looks_at_the_top_k_slots(self):
        from dfgg_ltr.counter_analysis import off_type_top_k

        features, scores, group, champion_ids, item_ids = self._inputs()

        result = off_type_top_k(
            features, scores, group, champion_ids, item_ids,
            item_types={1: "AD", 2: "AP", 3: "AD"},
            champion_ap_shares={157: 0.0}, top_k=1)

        assert result["typed_slots"] == 1
        assert result["off_type_slots"] == 0
