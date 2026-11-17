"""기준 모델과 새 모델을 같은 데이터로 학습해 나란히 비교하는 보고서.

작은 가짜 데이터로 처음부터 끝까지 돌려, 보고서에 판단에 필요한 항목이 모두 들어가는지 본다.
숫자가 맞는지는 각 부품(metrics, compare, situation, importance)의 테스트가 본다.
"""
import json

import numpy as np
import pytest

from dfgg_ltr.compare import rank_change
from dfgg_ltr.metrics import evaluate_ranking
from dfgg_ltr.situation import load_item_catalog
from dfgg_ltr.situation_report import SITUATION_FEATURES_NOT_RESEARCHED, main, measure_split

BASELINE_NAMES = ["build_score", "counter_score", "purchased_item_count"]
SITUATION_NAMES = [
    "enemy_core_gold_sum", "enemy_spell_damage_gold_share", "enemy_armor_gold_share",
    "enemy_spell_block_gold_share",
    "enemy_kill_sum", "enemy_mage_kill_share", "enemy_assassin_kill_share",
    "enemy_marksman_kill_share", "enemy_top_killer_kill_share",
    "lane_level_lead", "lane_opponent_kills", "lane_core_gold_lead",
    "my_level_lead", "my_kills", "my_deaths", "my_assists",
    "team_kill_lead", "team_assist_lead", "team_level_lead", "team_core_gold_lead",
]
FEATURE_NAMES = BASELINE_NAMES + SITUATION_NAMES
MAGIC_RESIST, ARMOR_PEN, MAGIC_PEN, PLAIN = 3111, 3036, 3135, 3031
ITEMS = [MAGIC_RESIST, ARMOR_PEN, MAGIC_PEN, PLAIN, 3032, 3033]


@pytest.fixture(scope="module")
def files(tmp_path_factory):
    """query 400개 × 후보 6개. 상대 주문력 비중이 높으면 마법 저항 아이템이 정답이 되기 쉽다."""
    tmp_path = tmp_path_factory.mktemp("situation-report")
    schema = tmp_path / "feature_schema.json"
    schema.write_text(json.dumps({
        "schema_fingerprint": "full789", "feature_count": len(FEATURE_NAMES),
        "baseline_schema_fingerprint": "base456", "baseline_feature_count": len(BASELINE_NAMES),
        "feature_names": FEATURE_NAMES,
    }), encoding="utf-8")

    catalog = tmp_path / "item_catalog.json"
    catalog.write_text(json.dumps({"items": [
        {"item_id": MAGIC_RESIST, "name": {"en_US": "Magic Resist Item"}, "tags": ["SpellBlock"], "total_gold": 1},
        {"item_id": ARMOR_PEN, "name": {"en_US": "Armor Pen Item"}, "tags": ["ArmorPenetration"], "total_gold": 1},
        {"item_id": MAGIC_PEN, "name": {"en_US": "Magic Pen Item"}, "tags": ["MagicPenetration"], "total_gold": 1},
        {"item_id": PLAIN, "name": {"en_US": "Plain Item"}, "tags": ["Damage"], "total_gold": 1},
    ]}), encoding="utf-8")

    rng = np.random.default_rng(0)
    data = tmp_path / "train.jsonl"
    with data.open("w", encoding="utf-8") as out:
        for query in range(400):
            situation = rng.random(len(SITUATION_NAMES))
            situation[SITUATION_NAMES.index("lane_level_lead")] = rng.integers(-3, 4)
            unknown = query % 10 == 0  # 열 중 하나는 상황을 모른다
            enemy_ap_share = situation[SITUATION_NAMES.index("enemy_spell_damage_gold_share")]
            base = rng.random((len(ITEMS), len(BASELINE_NAMES)))
            relevance = base[:, 0].copy()
            relevance[0] += 0.0 if unknown else enemy_ap_share  # 0번 후보가 마법 저항 아이템
            best = int(np.argmax(relevance))
            for candidate, item_id in enumerate(ITEMS):
                features = base[candidate].tolist() + ([None] * len(SITUATION_NAMES) if unknown
                                                       else situation.tolist())
                out.write(json.dumps({
                    "qid": f"M{query}#157#{query % 3}", "label": 3 if candidate == best else 0,
                    "item_id": item_id, "match_id": f"M{query}", "patch": "16.16",
                    "champion_id": 157, "position": "MID", "purchase_step": query % 3,
                    "split_game": "train" if query % 4 else "test",
                    "split_patch": "train" if query % 5 else "test",
                    "features": features,
                }) + "\n")
    return data, schema, catalog


@pytest.fixture(scope="module")
def report(files):
    """보고서는 모델 셋을 두 분할로 학습해야 해서 한 번만 만들고 테스트들이 같이 읽는다."""
    data, schema, catalog = files
    out = data.parent / "report.md"
    main(["--data", str(data), "--schema", str(schema), "--catalog", str(catalog),
          "--rounds", "30", "--out", str(out),
          "--targets", f"퍼센트 관통 아이템:enemy_armor_gold_share:{ARMOR_PEN}"])
    return out.read_text(encoding="utf-8")


@pytest.fixture(scope="module")
def measured(files):
    """game split의 수치. 표로 옮기기 전의 값이라 숫자를 직접 검증할 수 있다."""
    data, schema, catalog = files
    return measure_split(data, schema, load_item_catalog(catalog), "game", rounds=30, noise_seed=1,
                         targets=[("퍼센트 관통 아이템", "enemy_armor_gold_share", {ARMOR_PEN})])


class TestMeasuredNumbers:
    """표의 글자가 아니라 숫자를 본다 — 모델이나 점수가 뒤바뀌면 여기서 잡힌다."""

    def test_the_baseline_models_see_only_the_baseline_features(self, measured):
        """기준 모델 둘은 기준 계약의 칸만 보고, 새 모델은 스키마 전체를 본다."""
        assert measured["models"]["baseline"].num_feature() == len(BASELINE_NAMES)
        assert measured["models"]["reseeded"].num_feature() == len(BASELINE_NAMES)
        assert measured["models"]["situational"].num_feature() == len(FEATURE_NAMES)

    def test_each_metric_row_comes_from_its_own_models_scores(self, measured):
        test_set = measured["test_set"]
        for name in ("baseline", "reseeded", "situational"):
            expected = evaluate_ranking(test_set.labels, measured["scores"][name], list(test_set.group))
            assert measured["metrics"][name] == expected

    def test_the_new_model_ranks_better_when_the_situation_decides_the_answer(self, measured):
        """가짜 데이터는 상대 주문력 비중이 정답을 정한다. 그 값을 보는 새 모델이 더 잘 맞혀야 한다."""
        assert measured["metrics"]["situational"]["ndcg@1"] > measured["metrics"]["baseline"]["ndcg@1"]

    def test_the_noise_floor_compares_the_two_baseline_models(self, measured):
        """잡음 바닥은 씨앗만 다른 두 기준 모델끼리의 차이다. 새 모델이 섞이면 안 된다."""
        test_set = measured["test_set"]
        scores = measured["scores"]
        group, item_ids = list(test_set.group), test_set.item_ids

        assert measured["rank_change"]["noise"] == rank_change(
            scores["baseline"], scores["reseeded"], group, item_ids)
        assert measured["rank_change"]["new"] == rank_change(
            scores["baseline"], scores["situational"], group, item_ids)

    def test_rank_change_among_known_queries_has_its_own_noise_floor(self, measured):
        """상황을 아는 query만 볼 때는 잡음 바닥도 같은 query로 잰다."""
        assert measured["rank_change"]["new_known"]["queries"] == measured["known_queries"]
        assert measured["rank_change"]["noise_known"]["queries"] == measured["known_queries"]

    def test_raising_the_enemy_spell_damage_share_lifts_magic_resist_items(self, measured):
        """값 바꿔 넣기: 상대 주문력 비중을 올리면 마법 저항 아이템의 평균 순위가 올라간다(숫자는 작아진다)."""
        effect = next(row for row in measured["substitution"] if "SpellBlock" in row["label"])

        assert effect["mean_rank_high"] < effect["mean_rank_low"]
        assert effect["top1_share_high"] > effect["top1_share_low"]

    def test_the_situation_value_that_decides_the_answer_pushes_predictions_the_most(self, measured):
        """가짜 데이터에서 정답을 정하는 값이 평균 |SHAP|으로 가장 크다. feature 행은 그 순서로 나온다.

        "섞었을 때 하락"은 여기서 단언하지 않는다. 후보가 6개뿐인 가짜 데이터에서는 ndcg@5가 거의 움직이지 않아
        전부 잡음 범위다. 그 계산이 맞는지는 test_situation.py가 정확한 숫자로 본다.
        """
        features = [row for row in measured["importance"] if row["kind"] == "feature"]

        assert features[0]["name"] == "enemy_spell_damage_gold_share"
        assert features[0]["shap"] > 2 * features[1]["shap"]
        assert len(features) == len(SITUATION_NAMES)

    def test_importance_also_covers_each_group_and_the_whole_situation(self, measured):
        """한 값씩 섞으면 비슷한 값이 빈자리를 메운다. 묶음 단위와 상황 전체도 함께 잰다."""
        groups = {row["name"] for row in measured["importance"] if row["kind"] == "group"}

        assert groups == {"상대의 아이템", "상대 중 누가 컸나", "맞라인", "내 상태", "팀 상태", "상황 전체"}


def test_report_stops_before_training_when_a_scenario_has_no_target_item(tmp_path, files):
    """태그에 해당하는 아이템이 아이템 정보에 없으면 그 줄은 조용히 빈칸이 된다. 학습 전에 멈추고 알린다."""
    data, schema, _ = files
    catalog = tmp_path / "item_catalog.json"
    catalog.write_text(json.dumps({"items": [
        {"item_id": MAGIC_RESIST, "name": {}, "tags": ["SpellBlock"], "total_gold": 1},
    ]}), encoding="utf-8")

    with pytest.raises(ValueError, match="대상 아이템"):
        main(["--data", str(data), "--schema", str(schema), "--catalog", str(catalog), "--rounds", "30"])


def test_report_compares_the_baseline_and_the_new_model_on_both_splits(report):
    """두 분할 각각에 기준 모델과 새 모델의 지표가 나란히 있다."""
    assert report.count("| 기준 모델 (앞 3개) |") == 2
    assert report.count("| **새 모델 (23개)** |") == 2
    assert "game split" in report and "patch split" in report
    assert "ndcg@1" in report and "hit_rate@5" in report


def test_report_puts_rank_change_next_to_the_noise_floor(report):
    """순위 변화는 잡음 바닥(씨앗만 다른 두 기준 모델의 차이)과 함께 읽어야 한다."""
    assert "기준 모델 → 새 모델" in report
    assert "잡음 바닥" in report
    assert "잡음 바닥 (상황을 아는 query만)" in report
    assert "1순위가 바뀐 query" in report and "상위 3개 구성이 바뀐 query" in report


def test_report_shows_how_tagged_items_move_when_a_situation_value_changes(report):
    """값 바꿔 넣기: 태그로 고른 아이템과, 실행할 때 번호로 지정한 아이템 묶음이 모두 나온다."""
    assert "마법 저항 아이템 (SpellBlock)" in report
    assert "방어구 관통 아이템 (ArmorPenetration)" in report
    assert "마법 관통 아이템 (MagicPenetration)" in report
    assert f"퍼센트 관통 아이템 (번호 지정: {ARMOR_PEN})" in report


def test_report_splits_real_games_by_the_observed_lane_state(report):
    """실제 판 나눠 보기: 맞라인에서 앞선 판과 뒤처진 판을 따로 본다."""
    assert "맞라인에서 앞선 판" in report
    assert "맞라인에서 뒤처진 판" in report


def test_report_lists_the_importance_of_every_situation_feature(report):
    """상황 feature 20개의 중요도가 하나씩 있고, 조사에서 확인하지 않은 15개가 표시된다."""
    for name in SITUATION_NAMES:
        assert f"| {name} |" in report
    assert len(SITUATION_FEATURES_NOT_RESEARCHED) == 15
    assert report.count("확인하지 않음") >= 15
    assert "| 묶음: 상황 전체 |" in report
    assert "요청 단위로 섞" in report


def test_report_shows_how_often_item_values_are_empty_at_the_first_cores(report):
    """첫 번째·두 번째 Core를 고를 때 아이템 값이 비는 비율."""
    assert "첫 번째 Core" in report and "두 번째 Core" in report
    assert "| enemy_core_gold_sum |" in report


def test_report_states_how_much_of_the_test_set_knows_the_situation(report):
    assert "상황을 아는 query" in report


def test_report_warns_that_absolute_numbers_are_optimistic(report):
    """학습을 멈추는 시점을 테스트 데이터로 정하므로 절대 수치는 약간 좋게 나온다. 읽는 사람이 알아야 한다."""
    assert "절대 수치" in report


def test_report_states_what_the_offline_numbers_cannot_show(report):
    """학습 스냅샷은 구매 직전의 상태다. 실제 요청은 그보다 일찍 오는데, 이 차이는 오프라인 수치에 안 보인다."""
    assert "구매 직전" in report
    assert "씨앗 하나" in report


def test_report_shows_concrete_examples_for_the_crew(report):
    """같은 조합에서 상황 값만 바꿨을 때 추천이 어떻게 달라지는지 예시로 보여 준다."""
    assert "예시" in report
    assert "Magic Resist Item" in report
