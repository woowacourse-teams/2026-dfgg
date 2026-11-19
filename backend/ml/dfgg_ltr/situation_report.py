"""기준 모델과 새 모델을 같은 데이터로 학습해 나란히 비교한다.

  * 기준 모델: 스키마의 앞쪽 feature(기준 계약)만 본다. 지금 운영 모델과 입력·학습 설정이 같다.
  * 새 모델: 게임 상황 feature까지 본다.

서버에 올라가 있는 모델은 재지 않는다 — 평가용 경기를 학습 때 이미 봤을 수 있어 수치를 믿을 수 없다.

같은 데이터로 씨앗만 바꿔 기준 모델을 한 번 더 학습해 "다시 학습했을 때의 흔들림"(잡음 바닥)을 함께 낸다.
새 모델의 변화가 그 폭보다 커야 상황 feature 덕으로 읽을 수 있다.

수치를 재는 일(`measure_split`)과 표로 옮기는 일(`render_split`)을 나눴다. 숫자는 앞쪽에서 검증한다.

실행(backend/ml):
  uv run python -m dfgg_ltr.situation_report --data data/train.jsonl --out <보고서 경로> \\
      --targets "퍼센트 관통 아이템:enemy_armor_gold_share:<번호>,<번호>"
"""
from __future__ import annotations

import argparse
from pathlib import Path
from typing import Callable

import lightgbm as lgb
import numpy as np

from dfgg_ltr.compare import rank_change, top_items
from dfgg_ltr.dataset import Dataset, baseline_view, load_dataset, prefix_view
from dfgg_ltr.importance import shap_importance_by_group
from dfgg_ltr.metrics import evaluate_ranking
from dfgg_ltr.situation import (
    SITUATION_MATCHES,
    empty_value_rates,
    items_with_tag,
    load_item_catalog,
    low_and_high_values,
    query_permutation_importance,
    query_values,
    substitute_situation,
    substitution_effect,
    top1_share,
)
from dfgg_ltr.train import train

METRIC_COLUMNS = ["ndcg@1", "ndcg@3", "ndcg@5", "mrr", "hit_rate@5"]

#: 상황을 알면 항상 값이 있는 feature. 비중처럼 "아는데도 비는" 값이 아니라서 상황을 아는 query를 가르는 데 쓴다.
KNOWN_SITUATION_MARKER = "my_kills"

#: (바꿀 feature, 아이템 태그, 보고서에 쓸 이름). 아이템은 번호가 아니라 Java가 내보낸 태그로 고른다.
TAG_SCENARIOS = [
    ("enemy_spell_damage_gold_share", "SpellBlock", "마법 저항 아이템"),
    ("enemy_armor_gold_share", "ArmorPenetration", "방어구 관통 아이템"),
    ("enemy_spell_block_gold_share", "MagicPenetration", "마법 관통 아이템"),
    ("enemy_marksman_kill_share", "Armor", "방어구 아이템"),
]

#: 실제 판 나눠 보기에서 "방어 아이템"으로 볼 태그.
DEFENSIVE_TAGS = ("Armor", "SpellBlock")
LANE_LEVEL_LEAD = "lane_level_lead"

#: Core만 봐서 비기 쉬운 값들 — 아이템에서 계산하는 feature.
ITEM_VALUE_FEATURES = [
    "enemy_core_gold_sum", "enemy_spell_damage_gold_share", "enemy_armor_gold_share",
    "enemy_spell_block_gold_share", "team_core_gold_lead", "lane_core_gold_lead",
]

#: 상황 feature의 묶음. 한 값씩 섞으면 비슷한 값이 빈자리를 메워 과소평가되므로 묶음 단위로도 잰다.
SITUATION_GROUPS = {
    "상대의 아이템": ["enemy_core_gold_sum", "enemy_spell_damage_gold_share", "enemy_armor_gold_share",
                 "enemy_spell_block_gold_share"],
    "상대 중 누가 컸나": ["enemy_kill_sum", "enemy_mage_kill_share", "enemy_assassin_kill_share",
                   "enemy_marksman_kill_share", "enemy_top_killer_kill_share"],
    "맞라인": ["lane_level_lead", "lane_opponent_kills", "lane_core_gold_lead"],
    "내 상태": ["my_level_lead", "my_kills", "my_deaths", "my_assists"],
    "팀 상태": ["team_kill_lead", "team_assist_lead", "team_level_lead", "team_core_gold_lead"],
}
WHOLE_SITUATION = "상황 전체"

#: 사전 조사에서 "순위와 관련이 있다"를 확인하지 않고 넣은 feature. 모델이 실제로 쓰는지 따로 본다.
SITUATION_FEATURES_NOT_RESEARCHED = frozenset({
    "enemy_spell_block_gold_share",
    "enemy_mage_kill_share", "enemy_assassin_kill_share", "enemy_marksman_kill_share",
    "lane_level_lead", "lane_opponent_kills", "lane_core_gold_lead",
    "my_level_lead", "my_kills", "my_deaths", "my_assists",
    "team_kill_lead", "team_assist_lead", "team_level_lead", "team_core_gold_lead",
})


#: 후보별 상황 맞춤 값. 사전 조사 없이 넣었고, 후보마다 값이 달라 요청 단위로 섞을 수 없다.
CANDIDATE_MATCH_FEATURES = frozenset(feature for feature, _, _ in SITUATION_MATCHES)


def researched(feature: str) -> bool:
    """사전 조사에서 순위와의 관계를 확인한 feature인가. 후보별 상황 맞춤 값은 조사 없이 넣었다."""
    return feature not in SITUATION_FEATURES_NOT_RESEARCHED and feature not in CANDIDATE_MATCH_FEATURES

EXAMPLE_LIMIT = 5


def _predictor(booster: lgb.Booster) -> Callable[[np.ndarray], np.ndarray]:
    return lambda features: booster.predict(
        features, num_iteration=booster.best_iteration, raw_score=True)


def _column(dataset: Dataset, name: str) -> int:
    if name not in dataset.feature_names:
        raise ValueError(f"스키마에 없는 feature입니다: {name}. 학습 데이터를 다시 export했는지 확인하세요.")
    return dataset.feature_names.index(name)


def parse_target(text: str) -> tuple[str, str, set[int]]:
    """'이름:feature:번호,번호' — 태그로 가를 수 없는 아이템 묶음을 실행할 때 번호로 지정한다."""
    parts = text.split(":")
    if len(parts) != 3:
        raise ValueError(f"--targets는 '이름:feature:번호,번호' 모양이어야 합니다: {text}")
    label, feature, ids = parts
    return label, feature, {int(item_id) for item_id in ids.split(",")}


def scenarios_of(catalog: dict[int, dict],
                 targets: list[tuple[str, str, set[int]]]) -> list[tuple[str, str, set[int]]]:
    """(바꿀 feature, 보고서에 쓸 이름, 대상 아이템 번호들). 대상이 하나도 없으면 멈춘다.

    태그 이름이 아이템 정보와 맞지 않으면 그 줄이 조용히 빈칸으로 나온다. 모델 셋을 학습한 뒤에야 알게 되면
    늦으므로 학습 전에 확인한다.
    """
    scenarios = [(feature, f"{label} ({tag})", items_with_tag(catalog, tag))
                 for feature, tag, label in TAG_SCENARIOS]
    scenarios += [(feature, f"{label} (번호 지정: {','.join(str(item_id) for item_id in sorted(ids))})", ids)
                  for label, feature, ids in targets]
    empty = [label for _, label, ids in scenarios if not ids]
    if empty:
        raise ValueError(f"대상 아이템이 하나도 없습니다: {empty}. 아이템 정보의 태그 이름을 확인하세요.")
    return scenarios


def _item_name(catalog: dict[int, dict], item_id: int) -> str:
    names = catalog.get(item_id, {}).get("name") or {}
    for locale in ("ko_KR", "ko-KR", "en_US"):
        if locale in names:
            return names[locale]
    return next(iter(names.values()), str(item_id))


def measure_split(data: Path, schema: Path, catalog: dict[int, dict], split: str, rounds: int,
                  noise_seed: int, targets: list[tuple[str, str, set[int]]],
                  reference_count: int | None = None) -> dict:
    """한 분할에서 모델 셋을 학습하고 보고서에 들어갈 수치를 잰다.

    :param reference_count: 기준 모델이 볼 앞쪽 칸 수. 주지 않으면 기준 계약(지금 운영 모델의 칸)이다.
        다음 상황 모델을 이전 상황 모델과 비교할 때 이전 모델의 칸 수를 준다
    """
    scenarios = scenarios_of(catalog, targets)

    train_set = load_dataset(data, schema, split, "train")
    test_set = load_dataset(data, schema, split, "test")
    if reference_count is None:
        baseline_train, baseline_test = baseline_view(train_set), baseline_view(test_set)
    else:
        if train_set.baseline_feature_count is None:
            # "상황 전체"의 범위(기준 계약 뒤 전부)를 정할 수 없다. 지어내지 않고 멈춘다.
            raise ValueError("스키마 파일에 기준 계약(baseline_feature_count)이 없습니다. 학습 데이터를 다시 export해야 합니다.")
        baseline_train, baseline_test = prefix_view(train_set, reference_count), prefix_view(test_set, reference_count)
    baseline_count = len(baseline_test.feature_names)

    # 기준 모델과 새 모델은 씨앗을 주지 않는다 — 평소 학습과 같은 조건이다. 잡음 바닥용 모델만 씨앗을 바꾼다.
    models = {
        "baseline": train(baseline_train, baseline_test, rounds)[0],
        "reseeded": train(baseline_train, baseline_test, rounds, noise_seed)[0],
        "situational": train(train_set, test_set, rounds)[0],
    }
    # 기준 모델 둘은 기준 계약의 칸만 본다. 점수도 그 칸만 남긴 같은 표본에서 낸다.
    scores = {
        "baseline": _predictor(models["baseline"])(baseline_test.features),
        "reseeded": _predictor(models["reseeded"])(baseline_test.features),
        "situational": _predictor(models["situational"])(test_set.features),
    }

    group, item_ids = list(test_set.group), test_set.item_ids
    known = ~np.isnan(query_values(test_set.features, group, _column(test_set, KNOWN_SITUATION_MARKER)))
    predict = _predictor(models["situational"])

    return {
        "split": split,
        "train_queries": len(train_set.group),
        "test_queries": len(group),
        "known_queries": int(known.sum()),
        "baseline_count": baseline_count,
        "feature_count": len(test_set.feature_names),
        "best_iteration": {name: model.best_iteration for name, model in models.items()},
        "models": models,
        "scores": scores,
        "test_set": test_set,
        "metrics": {name: evaluate_ranking(test_set.labels, model_scores, group)
                    for name, model_scores in scores.items()},
        "rank_change": {
            "new": rank_change(scores["baseline"], scores["situational"], group, item_ids),
            "noise": rank_change(scores["baseline"], scores["reseeded"], group, item_ids),
            "new_known": rank_change(scores["baseline"], scores["situational"], group, item_ids, queries=known),
            "noise_known": rank_change(scores["baseline"], scores["reseeded"], group, item_ids, queries=known),
        },
        "substitution": [
            {"feature": feature, "label": label,
             **substitution_effect(predict, test_set, _column(test_set, feature), target_items, known,
                                   catalog=catalog)}
            for feature, label, target_items in scenarios
        ],
        "observed": _observed_split(test_set, catalog, known, scores),
        "importance": _importance(models["situational"], predict, test_set, baseline_count, catalog),
        "empty": empty_value_rates(
            test_set, [_column(test_set, name) for name in ITEM_VALUE_FEATURES], steps=(0, 1), queries=known),
        "examples": _examples(predict, test_set, catalog, known),
    }


def _observed_split(test_set: Dataset, catalog: dict[int, dict], known: np.ndarray, scores: dict) -> list[dict]:
    group, item_ids = list(test_set.group), test_set.item_ids
    lead = query_values(test_set.features, group, _column(test_set, LANE_LEVEL_LEAD))
    defensive = set().union(*(items_with_tag(catalog, tag) for tag in DEFENSIVE_TAGS))

    rows = []
    # NaN과의 비교는 False라 맞라인 상대를 모르는 query는 어느 쪽에도 들어가지 않는다.
    for name, chosen in (("맞라인에서 앞선 판", known & (lead > 0)), ("맞라인에서 뒤처진 판", known & (lead < 0))):
        change = rank_change(scores["baseline"], scores["situational"], group, item_ids, queries=chosen)
        rows.append({
            "name": name,
            "queries": int(chosen.sum()),
            "top1_changed": change["top1_changed"],
            "defensive_top1_baseline": top1_share(scores["baseline"], group, item_ids, defensive, chosen),
            "defensive_top1_situational": top1_share(scores["situational"], group, item_ids, defensive, chosen),
        })
    return rows


def _importance(situational: lgb.Booster, predict: Callable[[np.ndarray], np.ndarray], test_set: Dataset,
                baseline_count: int, catalog: dict[int, dict]) -> list[dict]:
    """기준 모델에 없던 feature 하나씩, 묶음마다, 그리고 상황 전체의 중요도.

    섞었을 때의 하락은 요청 단위로 섞어서 잰다. 행 단위로 섞으면 같은 요청의 후보마다 다른 상황이 들어가
    순위와 무관한 값에도 중요도가 생긴다. 후보별 상황 맞춤 칸은 섞지 않고, 섞인 상황 값과 후보 태그로 다시
    계산한다. 그래서 맞춤 칸 하나만의 하락은 잴 수 없어 비워 두고(SHAP만), 그 칸은 곱하는 상황 값의 묶음에 넣는다.

    "상황 전체"는 기준을 어디로 잡든 기준 계약 뒤의 모든 칸이다. 기준 칸 수를 늘려도 상황 전체의 몫은 같은 범위로 잰다.
    """
    new_names = test_set.feature_names[baseline_count:]
    situation_start = test_set.baseline_feature_count if test_set.baseline_feature_count is not None else baseline_count
    features = {name: [_column(test_set, name)] for name in new_names}
    groups = {name: [_column(test_set, member) for member in _with_candidate_matches(members, test_set)]
              for name, members in SITUATION_GROUPS.items()}
    groups[WHOLE_SITUATION] = [_column(test_set, name) for name in test_set.feature_names[situation_start:]]

    columns = {**features, **groups}
    shap = shap_importance_by_group(situational, test_set.features, columns)
    permutation = query_permutation_importance(predict, test_set, columns, catalog=catalog)

    rows = [{"kind": "feature", "name": name, "shap": shap[name],
             "permutation": float("nan") if name in CANDIDATE_MATCH_FEATURES else permutation[name],
             "researched": researched(name)} for name in features]
    rows.sort(key=lambda row: -row["shap"])
    rows += [{"kind": "group", "name": name, "shap": shap[name], "permutation": permutation[name]}
             for name in groups]
    return rows


def _with_candidate_matches(members: list[str], dataset: Dataset) -> list[str]:
    """묶음의 상황 값에, 그 값을 곱하는 후보별 맞춤 칸(데이터에 있는 것만)을 더한다."""
    matches = [feature for feature, source, _ in SITUATION_MATCHES
               if source in members and feature in dataset.feature_names]
    return members + matches


def _examples(predict: Callable[[np.ndarray], np.ndarray], test_set: Dataset, catalog: dict[int, dict],
              known: np.ndarray) -> dict:
    """같은 요청에서 상황 값 하나만 바꿨을 때 상위 3개가 어떻게 달라지는지."""
    feature, _, _ = TAG_SCENARIOS[0]
    column = _column(test_set, feature)
    group, item_ids = list(test_set.group), test_set.item_ids
    low, high = low_and_high_values(test_set, column, known)
    low_tops = top_items(predict(substitute_situation(test_set, column, low, known, catalog)), group, item_ids, 3)
    high_tops = top_items(predict(substitute_situation(test_set, column, high, known, catalog)), group, item_ids, 3)

    rows = []
    for qid, chosen, low_top, high_top in zip(test_set.qids, known, low_tops, high_tops):
        if chosen and low_top[:1] != high_top[:1]:
            rows.append((qid, [_item_name(catalog, item_id) for item_id in low_top],
                         [_item_name(catalog, item_id) for item_id in high_top]))
            if len(rows) == EXAMPLE_LIMIT:
                break
    return {"feature": feature, "low": low, "high": high, "rows": rows}


def _signed(value: float) -> str:
    return "—" if np.isnan(value) else f"{value:+.4f}"


def _percent(value: float) -> str:
    return "—" if np.isnan(value) else f"{100 * value:.1f}%"


def render_split(measured: dict) -> str:
    """잰 수치를 표로 옮긴다. 계산은 하지 않는다."""
    iterations = measured["best_iteration"]
    lines = [
        f"## {measured['split']} split", "",
        f"train query {measured['train_queries']:,} / test query {measured['test_queries']:,} · "
        f"상황을 아는 query {measured['known_queries']:,} "
        f"({_percent(measured['known_queries'] / max(measured['test_queries'], 1))}) · "
        f"best_iteration: 기준 {iterations['baseline']} / 기준(씨앗만 다름) {iterations['reseeded']} / "
        f"새 모델 {iterations['situational']}",
        "",
    ]
    lines += _render_metrics(measured)
    lines += _render_rank_change(measured["rank_change"])
    lines += _render_substitution(measured["substitution"])
    lines += _render_observed(measured["observed"])
    lines += _render_importance(measured["importance"])
    lines += _render_empty(measured["empty"])
    lines += _render_examples(measured["examples"])
    return "\n".join(lines)


def _render_metrics(measured: dict) -> list[str]:
    metrics = measured["metrics"]
    baseline, reseeded, situational = metrics["baseline"], metrics["reseeded"], metrics["situational"]

    def row(name: str, values: dict, signed: bool = False) -> str:
        cells = " | ".join(f"{values[column]:+.4f}" if signed else f"{values[column]:.4f}"
                           for column in METRIC_COLUMNS)
        return f"| {name} | {cells} | {values.get('queries', '')} |"

    difference = {column: situational[column] - baseline[column] for column in METRIC_COLUMNS}
    noise = {column: abs(reseeded[column] - baseline[column]) for column in METRIC_COLUMNS}
    return [
        "### 지표", "",
        "| 방법 | " + " | ".join(METRIC_COLUMNS) + " | query |",
        "|---" * (len(METRIC_COLUMNS) + 2) + "|",
        row(f"기준 모델 (앞 {measured['baseline_count']}개)", baseline),
        row("기준 모델, 씨앗만 다름", reseeded),
        row(f"**새 모델 ({measured['feature_count']}개)**", situational),
        row("새 모델 − 기준 모델", difference, signed=True),
        row("잡음 바닥 (두 기준 모델의 차이)", noise),
        "",
    ]


def _render_rank_change(changes: dict) -> list[str]:
    def row(name: str, change: dict) -> str:
        return (f"| {name} | {_percent(change['top1_changed'])} | {_percent(change['top3_changed'])} "
                f"| {change['queries']:,} |")

    return [
        "### 순위 변화", "",
        "| 비교 | 1순위가 바뀐 query | 상위 3개 구성이 바뀐 query | query |",
        "|---|---|---|---|",
        row("기준 모델 → 새 모델", changes["new"]),
        row("잡음 바닥: 기준 모델 → 씨앗만 다른 기준 모델", changes["noise"]),
        row("기준 모델 → 새 모델 (상황을 아는 query만)", changes["new_known"]),
        row("잡음 바닥 (상황을 아는 query만)", changes["noise_known"]),
        "",
    ]


def _render_substitution(effects: list[dict]) -> list[str]:
    lines = [
        "### 값 바꿔 넣기 (새 모델)", "",
        "상황을 아는 query에서 값 하나만 하위 10% 값과 상위 10% 값으로 바꿔 다시 점수를 매겼다. "
        "실제로는 없는 조합이 생길 수 있어 방향을 확인하는 용도다.", "",
        "| 바꾼 값 | 낮음 → 높음 | 대상 아이템 | query | 평균 순위 | 1순위 비율 | 상위 3위 비율 |",
        "|---|---|---|---|---|---|---|",
    ]
    for effect in effects:
        lines.append(
            f"| {effect['feature']} | {effect['low_value']:.2f} → {effect['high_value']:.2f} | {effect['label']} "
            f"| {effect['queries']:,} | {effect['mean_rank_low']:.2f} → {effect['mean_rank_high']:.2f} "
            f"| {_percent(effect['top1_share_low'])} → {_percent(effect['top1_share_high'])} "
            f"| {_percent(effect['top3_share_low'])} → {_percent(effect['top3_share_high'])} |")
    lines.append("")
    return lines


def _render_observed(rows: list[dict]) -> list[str]:
    lines = [
        "### 실제 판 나눠 보기: 맞라인 레벨", "",
        "값을 바꾸지 않고 실제로 그랬던 판을 나눴다. 기준 모델은 맞라인 상태를 보지 못하므로, "
        "기준 모델에서도 나타나는 차이는 다른 요인(챔피언, 조합)이 섞인 것이다. "
        f"방어 아이템은 {'·'.join(DEFENSIVE_TAGS)} 태그가 있는 아이템이다.", "",
        "| 판 | query | 두 모델의 1순위가 다른 비율 | 방어 아이템이 1순위: 기준 모델 | 방어 아이템이 1순위: 새 모델 |",
        "|---|---|---|---|---|",
    ]
    for row in rows:
        lines.append(
            f"| {row['name']} | {row['queries']:,} | {_percent(row['top1_changed'])} "
            f"| {_percent(row['defensive_top1_baseline'])} | {_percent(row['defensive_top1_situational'])} |")
    lines.append("")
    return lines


def _render_importance(rows: list[dict]) -> list[str]:
    feature_count = sum(1 for row in rows if row["kind"] == "feature")
    lines = [
        f"### 상황 feature {feature_count}개의 중요도 (새 모델)", "",
        "평균 |SHAP|은 예측을 얼마나 밀었는지다. 섞었을 때 하락은 그 값을 **요청 단위로 섞어** 다른 요청의 값과 "
        "바꿨을 때 ndcg@5가 떨어지는 폭이다. 한 값만 섞으면 비슷한 값이 빈자리를 메워 작게 나올 수 있어, "
        "묶음과 상황 전체도 함께 쟀다. 후보별 상황 맞춤 칸(candidate_…)은 후보마다 값이 달라 섞지 않고 "
        "섞인 상황 값으로 다시 계산하므로 칸 하나만의 하락은 —로 두고, 곱하는 상황 값의 묶음에 함께 넣었다.", "",
        "| feature | 평균 \\|SHAP\\| | 섞었을 때 ndcg@5 하락 | 사전 조사 |",
        "|---|---|---|---|",
    ]
    for row in rows:
        if row["kind"] == "feature":
            researched = "확인함" if row["researched"] else "확인하지 않음"
            lines.append(f"| {row['name']} | {row['shap']:.4f} | {_signed(row['permutation'])} | {researched} |")
        else:
            lines.append(f"| 묶음: {row['name']} | {row['shap']:.4f} | {_signed(row['permutation'])} | |")
    lines.append("")
    return lines


def _render_empty(rates: list[dict]) -> list[str]:
    step_names = {0: "첫 번째 Core", 1: "두 번째 Core"}
    lines = [
        "### 아이템 값이 비는 비율", "",
        "아이템 값은 Core만 본다. 초반에는 아무도 Core를 사지 않아 값이 비거나 0이 된다. "
        "상황을 아는 query만 셌다. 맞라인 값(lane_core_gold_lead)은 맞라인 상대를 정하지 못해도 빈다.", "",
        "| 스냅샷 | feature | query | 비어 있음 | 0 |",
        "|---|---|---|---|---|",
    ]
    for rate in rates:
        lines.append(
            f"| {step_names[rate['purchase_step']]} | {rate['feature']} | {rate['queries']:,} "
            f"| {_percent(rate['missing'])} | {_percent(rate['zero'])} |")
    lines.append("")
    return lines


def _render_examples(examples: dict) -> list[str]:
    lines = [
        f"### 예시: {examples['feature']}만 바꿨을 때 (새 모델)", "",
        f"같은 요청에서 {examples['feature']}를 {examples['low']:.2f}(낮음)과 {examples['high']:.2f}(높음)으로 "
        f"바꿨을 때의 상위 3개다. 1순위가 달라진 요청을 앞에서부터 {EXAMPLE_LIMIT}개까지 보인다.", "",
        "| 요청 (경기#챔피언#구매 단계) | 낮을 때 | 높을 때 |",
        "|---|---|---|",
    ]
    for qid, low_top, high_top in examples["rows"]:
        lines.append(f"| {qid} | {' > '.join(low_top)} | {' > '.join(high_top)} |")
    if not examples["rows"]:
        lines.append("| (1순위가 달라진 요청이 없다) | | |")
    lines.append("")
    return lines


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="기준 모델과 새 모델(게임 상황 feature 포함)을 나란히 비교한다")
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--schema", type=Path, default=None,
                        help="주지 않으면 학습 데이터 옆의 feature_schema.json")
    parser.add_argument("--catalog", type=Path, default=None,
                        help="주지 않으면 학습 데이터 옆의 item_catalog.json")
    parser.add_argument("--rounds", type=int, default=500)
    parser.add_argument("--noise-seed", type=int, default=1,
                        help="잡음 바닥을 잴 때 기준 모델을 한 번 더 학습하는 씨앗")
    parser.add_argument("--targets", action="append", default=[],
                        help="'이름:feature:번호,번호' — 태그로 가를 수 없는 아이템 묶음을 번호로 지정한다. 여러 번 줄 수 있다")
    parser.add_argument("--reference-count", type=int, default=None,
                        help="기준 모델이 볼 앞쪽 칸 수. 주지 않으면 기준 계약(지금 운영 모델의 칸)이다. "
                             "다음 상황 모델을 이전 상황 모델(앞 70칸)과 비교할 때 70을 준다")
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args(argv)

    schema = args.schema or args.data.parent / "feature_schema.json"
    catalog = load_item_catalog(args.catalog or args.data.parent / "item_catalog.json")
    targets = [parse_target(text) for text in args.targets]
    # 대상 아이템이 없는 줄은 학습을 시작하기 전에 걸러 낸다.
    scenarios_of(catalog, targets)

    sections = [
        "# 게임 상황 feature 평가: 기준 모델과 새 모델", "",
        ("- 기준 모델은 스키마의 앞쪽 feature(기준 계약)만, 새 모델은 게임 상황 feature까지 본다. "
         if args.reference_count is None else
         f"- 기준 모델은 스키마의 앞 {args.reference_count}칸만, 새 모델은 스키마 전체를 본다. ")
        + "둘은 같은 학습 데이터, 같은 학습 설정으로 학습했다.",
        "- **절대 수치는 약간 좋게 나온다.** 학습을 멈추는 시점을 테스트 데이터로 정하기 때문이다. "
        "두 모델이 같은 조건이라 비교에는 문제가 없다.",
        f"- 잡음 바닥은 씨앗만 바꿔(seed={args.noise_seed}) 다시 학습한 기준 모델과의 차이다. "
        "새 모델의 변화가 이보다 커야 상황 feature 덕으로 읽을 수 있다. "
        "씨앗 하나로 잰 값이라 우연히 작게 나올 수 있다. 판단이 아슬아슬하면 `--noise-seed`를 바꿔 다시 잰다.",
        "- **이 수치에 보이지 않는 것.** 학습 스냅샷의 상황은 Core를 사기 직전, 곧 구매 직전의 상태다. "
        "실제 요청은 그보다 이른 시점에도 오므로 킬·데스·Core 가격처럼 쌓이는 값은 학습 때보다 작게 들어온다. "
        "이 차이는 오프라인 수치로 알 수 없고 실제 게임에서 확인한다.",
        "- 서버에 올라가 있는 모델은 재지 않았다.", "",
    ]
    for split in ("game", "patch"):
        sections.append(render_split(
            measure_split(args.data, schema, catalog, split, args.rounds, args.noise_seed, targets,
                          reference_count=args.reference_count)))

    report = "\n".join(sections)
    print(report)
    if args.out is not None:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report, encoding="utf-8")


if __name__ == "__main__":
    main()
