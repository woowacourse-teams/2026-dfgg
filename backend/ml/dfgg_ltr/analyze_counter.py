"""T15 counter-only 실패 분석. 검증된 모듈들을 실데이터에 붙여 리포트를 만든다.

    uv run python -m dfgg_ltr.analyze_counter \
        --data data/train_noleak.jsonl --schema data/feature_schema.json \
        --booster data/booster.txt --item-tags data/item_tags.json \
        --out ../tasks/eval-counter-failure.md
"""
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path

import lightgbm as lgb
import numpy as np

from dfgg_ltr.counter_analysis import (
    analyze_sources,
    champion_ap_share,
    item_type,
    off_type_top_k,
)
from dfgg_ltr.importance import permutation_importance_by_group, shap_importance_by_group
from dfgg_ltr.scenario import build_scenarios, percentile_profile

TOP_K = 5


def load_rows(data_path: Path, split_field: str, subset: str):
    by_qid: dict[str, list[dict]] = defaultdict(list)
    with data_path.open(encoding="utf-8") as file:
        for line in file:
            row = json.loads(line)
            if row[split_field] == subset:
                by_qid[row["qid"]].append(row)

    features, labels, group, champion_ids, item_ids = [], [], [], [], []
    for qid in sorted(by_qid):
        rows = by_qid[qid]
        group.append(len(rows))
        for row in rows:
            features.append([np.nan if value is None else value for value in row["features"]])
            labels.append(row["label"])
            champion_ids.append(row["champion_id"])
            item_ids.append(row["item_id"])
    return (np.array(features, dtype=float), np.array(labels), group,
            champion_ids, item_ids)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--schema", type=Path, required=True)
    parser.add_argument("--booster", type=Path, required=True)
    parser.add_argument("--item-tags", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    schema = json.loads(args.schema.read_text(encoding="utf-8"))
    names, group_names = schema["feature_names"], schema["feature_groups"]
    groups: dict[str, list[int]] = defaultdict(list)
    for index, group_name in enumerate(group_names):
        groups[group_name].append(index)

    booster = lgb.Booster(model_file=str(args.booster))
    features, labels, group, champion_ids, item_ids = load_rows(args.data, "split_game", "test")
    scores = booster.predict(features, raw_score=True)
    print(f"test query {len(group):,} / 행 {len(features):,}")

    sources = analyze_sources(features, scores, group, TOP_K, labels)

    shap = shap_importance_by_group(booster, features, groups)
    permutation = permutation_importance_by_group(
        booster, features, labels, group, groups, repeats=3, seed=17)

    raw_tags = json.loads(args.item_tags.read_text(encoding="utf-8"))
    item_types = {int(item_id): item_type(tags) for item_id, tags in raw_tags.items()}
    # 성향은 학습 구간의 실제 구매로 정한다. 평가 구간의 정답을 쓰면 답을 미리 본 셈이 된다.
    train_features, train_labels, _, train_champions, train_items = load_rows(
        args.data, "split_game", "train")
    shares = champion_ap_share(train_champions, train_items, train_labels, item_types)
    off_type = off_type_top_k(features, scores, group, champion_ids, item_ids,
                              item_types, shares, TOP_K)

    profile = percentile_profile(features)
    scenario_specs = [
        ("Counter↑ Build↓ Self↓", ["COUNTER"], ["BUILD", "SELF_SYNERGY"]),
        ("Build↑ Self↑ Counter↓", ["BUILD", "SELF_SYNERGY"], ["COUNTER"]),
        ("전부 중앙값", [], []),
        ("Counter만 최고치", ["COUNTER"], []),
        ("Build만 최고치", ["BUILD"], []),
    ]
    scenario_rows, scenario_names = build_scenarios(profile, groups, scenario_specs)
    scenario_scores = booster.predict(scenario_rows, raw_score=True)

    args.out.write_text(render(sources, shap, permutation, off_type, shares,
                               scenario_names, scenario_scores, len(group)), encoding="utf-8")
    print(f"리포트 저장: {args.out}")


def render(sources, shap, permutation, off_type, shares,
           scenario_names, scenario_scores, query_count) -> str:
    lines = ["# T15 Counter-only 실패 분석", "",
             f"game split test — query {query_count:,}건. 서빙과 같은 모델(`booster.txt`)로 채점했다.", "",
             "## 1. source 조합별 최종 순위", "",
             "`Top-5 칸 점유`는 전체 Top-5 자리(query×5) 중 이 조합이 가져간 비율이다.",
             "점유는 큰데 정답률이 낮은 조합이 자리를 낭비하고 있다.", "",
             "| 조합 | 후보 수 | Top-5 진입률 | Top-5 칸 점유 | 평균 순위 | 정답률 |",
             "|---|---|---|---|---|---|"]
    total_slots = query_count * TOP_K
    ordered = sorted(sources.items(),
                     key=lambda kv: -kv[1]["candidates"] * kv[1]["top_k_rate"])
    for name, stats in ordered:
        slots = stats["candidates"] * stats["top_k_rate"]
        lines.append("| {} | {:,} | {:.1%} | {:.1%} | {:.2f} | {:.2%} |".format(
            name, stats["candidates"], stats["top_k_rate"], slots / total_slots,
            stats["mean_rank"], stats.get("ground_truth_rate", float("nan"))))

    lines += ["", "## 2. 묶음별 중요도", "",
              "SHAP은 \"예측을 얼마나 밀었나\"(크기), permutation은 \"없으면 얼마나 나빠지나\"(쓸모)다.",
              "둘이 갈리는 묶음이 값은 크지만 예측력은 없는 경우다.", "",
              "| 묶음 | 평균 \\|SHAP\\| | NDCG@5 하락 |", "|---|---|---|"]
    for name in sorted(shap, key=lambda key: -shap[key]):
        lines.append(f"| {name} | {shap[name]:.4f} | {permutation[name]:+.4f} |")

    typed = off_type["typed_slots"]
    lines += ["", "## 3. 챔피언 성향과 어긋난 추천 (원래 결함의 최종 지표)", "",
              f"- 성향이 뚜렷한 챔피언의 Top-5 칸: **{typed:,}칸**",
              "- 그중 어긋난 추천: **{:,}칸 ({:.2%})**".format(
                  off_type["off_type_slots"], off_type["off_type_slots"] / typed if typed else 0),
              "- 그중 counter 단독이 올린 것: **{:,}칸 ({:.2%})**".format(
                  off_type["off_type_from_counter_only"],
                  off_type["off_type_from_counter_only"] / typed if typed else 0),
              f"- 성향이 판정된 챔피언 {len(shares)}명", "",
              "AD/AP hard filter를 두지 않기로 했으므로 0%가 목표가 아니다. off-meta 빌드는 남아야 한다.", "",
              "## 4. 합성 시나리오", "",
              "다른 값을 전부 중앙값으로 고정하고 한 묶음만 움직였을 때의 모델 점수.", "",
              "| 시나리오 | 점수 |", "|---|---|"]
    for name, score in zip(scenario_names, scenario_scores):
        lines.append(f"| {name} | {score:+.4f} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    main()
