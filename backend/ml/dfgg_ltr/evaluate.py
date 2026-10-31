"""학습된 LTR과 단일 feature 베이스라인을 같은 지표로 나란히 잰다.

`train.py`가 학습만 담당한다면 여기는 판정을 담당한다. 두 split(game/patch)과 ablation을
한 번에 돌려 `tasks/eval-ranking.md`를 만든다.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from dfgg_ltr.ablation import PATCH_AWARE, apply_feature_mask, select_feature_indices
from dfgg_ltr.dataset import Dataset, load_dataset
from dfgg_ltr.metrics import evaluate_ranking
from dfgg_ltr.train import PARAMS, train

METRIC_COLUMNS = ["ndcg@1", "ndcg@3", "ndcg@5", "mrr", "hit_rate@5"]


def baseline_scores(dataset: Dataset, feature_name: str, seed: int = 0) -> np.ndarray:
    """단일 feature를 그대로 점수로 쓴다. `random`이면 무작위 점수."""
    if feature_name == "random":
        return np.random.default_rng(seed).random(len(dataset.labels))
    if feature_name not in dataset.feature_names:
        raise ValueError(f"스키마에 없는 feature입니다: {feature_name}")
    column = dataset.features[:, dataset.feature_names.index(feature_name)]
    # 결측을 그대로 두면 정렬에서 앞으로 튀어 베이스라인이 실제보다 좋아 보인다.
    return np.where(np.isnan(column), -np.inf, column)


def render_report(title: str, rows: list[tuple[str, dict]]) -> str:
    lines = [f"### {title}", "",
             "| 방법 | " + " | ".join(METRIC_COLUMNS) + " | query |",
             "|---" * (len(METRIC_COLUMNS) + 2) + "|"]
    for name, metrics in rows:
        values = " | ".join(f"{metrics[column]:.4f}" if column in metrics else "—"
                            for column in METRIC_COLUMNS)
        lines.append(f"| {name} | {values} | {metrics.get('queries', 0)} |")
    lines.append("")
    return "\n".join(lines)


def _evaluate(dataset: Dataset, scores: np.ndarray) -> dict:
    return evaluate_ranking(dataset.labels, scores, list(dataset.group))


def evaluate_split(data_path: Path, schema_path: Path, split: str, rounds: int,
                   baselines: list[str]) -> tuple[list[tuple[str, dict]], dict]:
    train_set = load_dataset(data_path, schema_path, split, "train")
    test_set = load_dataset(data_path, schema_path, split, "test")

    booster, _ = train(train_set, test_set, rounds)
    ltr_metrics = _evaluate(test_set, booster.predict(
        test_set.features, num_iteration=booster.best_iteration, raw_score=True))

    rows: list[tuple[str, dict]] = [("**LTR (LambdaMART)**", ltr_metrics)]
    for name in baselines:
        rows.append((name, _evaluate(test_set, baseline_scores(test_set, name))))

    # 패치 민감 feature를 빼고 같은 조건으로 다시 학습한다.
    keep = select_feature_indices(train_set.feature_names, PATCH_AWARE)
    ablated_train = apply_feature_mask(train_set, keep)
    ablated_test = apply_feature_mask(test_set, keep)
    ablated_booster, _ = train(ablated_train, ablated_test, rounds)
    rows.insert(1, ("LTR − 패치 민감 feature", _evaluate(ablated_test, ablated_booster.predict(
        ablated_test.features, num_iteration=ablated_booster.best_iteration, raw_score=True))))

    info = {
        "train_queries": len(train_set.group),
        "test_queries": len(test_set.group),
        "features": len(train_set.feature_names),
        "dropped_patch_aware": len(train_set.feature_names) - len(keep),
        "best_iteration": booster.best_iteration,
    }
    return rows, info


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--schema", type=Path, default=Path("data/feature_schema.json"))
    parser.add_argument("--rounds", type=int, default=500)
    parser.add_argument("--label", default="")
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    baselines = ["build_score", "champion_base_rate_all", "self_synergy_score",
                 "ally_synergy_score", "counter_score", "random"]

    sections = []
    for split in ("game", "patch"):
        rows, info = evaluate_split(args.data, args.schema, split, args.rounds, baselines)
        sections.append(render_report(f"{split} split{args.label}", rows))
        sections.append(
            f"train query {info['train_queries']:,} / test query {info['test_queries']:,} · "
            f"feature {info['features']}개 (패치 민감 {info['dropped_patch_aware']}개 제외) · "
            f"best_iteration {info['best_iteration']}\n")

    report = "\n".join(sections)
    print(report)
    if args.out is not None:
        args.out.write_text(report, encoding="utf-8")


if __name__ == "__main__":
    main()
