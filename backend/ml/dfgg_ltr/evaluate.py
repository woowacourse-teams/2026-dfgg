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
from dfgg_ltr.dataset import Dataset, QueryMeta, load_dataset, load_query_meta
from dfgg_ltr.metrics import (
    PolicyValue,
    evaluate_ranking,
    hit_rate_by_outcome,
    policy_value,
    policy_value_difference,
    top1_matches,
)
from dfgg_ltr.reference_predict import predict_flat
from dfgg_ltr.train import PARAMS, train

METRIC_COLUMNS = ["ndcg@1", "ndcg@3", "ndcg@5", "mrr", "hit_rate@5"]
REFERENCE_MODEL_NAME = "현재 운영 모델"


def load_propensities(path: Path, qids: list[str]) -> np.ndarray:
    """`propensity.py`가 낸 query별 p를 qids 순서로 놓는다. 없는 query는 NaN."""
    by_qid: dict[str, float] = {}
    with Path(path).open(encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)
            by_qid[row["qid"]] = _nan_if_none(row["propensity"])
    return np.array([by_qid.get(qid, np.nan) for qid in qids])


def _nan_if_none(value) -> float:
    if value is None:
        return np.nan
    return float(value)


def model_scores(model: dict, dataset: Dataset) -> np.ndarray:
    """내보낸 모델 JSON(Java가 서빙하는 파일)으로 점수를 낸다. 스키마 지문이 다르면 거부한다."""
    if model["schema_fingerprint"] != dataset.schema_fingerprint:
        raise ValueError(
            f"모델과 데이터의 스키마 지문이 다릅니다: {model['schema_fingerprint']} != {dataset.schema_fingerprint}")
    return predict_flat(model["trees"], dataset.features)


def value_rows(dataset: Dataset, meta: QueryMeta, propensity: np.ndarray,
               scored: list[tuple[str, np.ndarray]], reference: str) -> list[tuple[str, dict]]:
    """방법마다 V와 기준 방법 대비 ΔV(같은 부트스트랩 표본으로 짝지음), 승패별 HitRate@5를 낸다."""
    matches = {name: top1_matches(dataset.labels, scores, dataset.group) for name, scores in scored}
    rows = []
    for name, scores in scored:
        value = policy_value(matches[name], propensity, meta.win, meta.match_ids)
        difference = policy_value_difference(matches[name], matches[reference], propensity, meta.win,
                                             meta.match_ids)
        rows.append((name, {"value": value, "difference": difference,
                            **hit_rate_by_outcome(dataset.labels, scores, dataset.group, meta.win)}))
    return rows


def render_value_report(title: str, rows: list[tuple[str, dict]], reference: str) -> str:
    lines = [f"### {title} — 승률 지표 V (기준: {reference})", "",
             "| 방법 | V (95% 구간) | ΔV %p (95% 구간) | ESS | 1순위 일치 | 커버리지 | 클리핑 "
             "| HitRate@5 승 / 패 | win lift |",
             "|---|---|---|---|---|---|---|---|---|"]
    for name, metrics in rows:
        value: PolicyValue = metrics["value"]
        difference = metrics["difference"]
        lines.append(
            f"| {name} | {value.value:.4f} ({value.lower:.4f}, {value.upper:.4f}) "
            f"| {difference['difference'] * 100:+.2f} ({difference['lower'] * 100:+.2f}, "
            f"{difference['upper'] * 100:+.2f}) | {value.ess:,.0f} | {value.matched:,} | {value.coverage:.3f} "
            f"| {value.clipped_share:.4f} | {metrics['hit_rate@5_win']:.4f} / {metrics['hit_rate@5_loss']:.4f} "
            f"| {metrics['win_lift']:+.4f} |")
    observed = rows[0][1]["value"].observed_win
    lines += ["", f"관측 승률(같은 query): {observed:.4f}. V는 이 값과 비교한다.", ""]
    return "\n".join(lines)


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
                   baselines: list[str], reference_model: dict | None = None
                   ) -> tuple[list[tuple[str, dict]], dict, list[tuple[str, np.ndarray]]]:
    """랭킹 지표 행, 요약 정보, V 계산에 쓸 (방법, 점수)를 돌려준다."""
    train_set = load_dataset(data_path, schema_path, split, "train")
    test_set = load_dataset(data_path, schema_path, split, "test")

    booster, _ = train(train_set, test_set, rounds)
    ltr_scores = booster.predict(test_set.features, num_iteration=booster.best_iteration, raw_score=True)
    ltr_metrics = _evaluate(test_set, ltr_scores)

    rows: list[tuple[str, dict]] = [("**LTR (LambdaMART)**", ltr_metrics)]
    scored: list[tuple[str, np.ndarray]] = [("LTR (LambdaMART)", ltr_scores)]
    if reference_model is not None:
        reference_scores = model_scores(reference_model, test_set)
        rows.append((REFERENCE_MODEL_NAME, _evaluate(test_set, reference_scores)))
        scored.insert(0, (REFERENCE_MODEL_NAME, reference_scores))
    for name in baselines:
        scores = baseline_scores(test_set, name)
        rows.append((name, _evaluate(test_set, scores)))
        scored.append((name, scores))

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
    return rows, info, scored


def value_section(data_path: Path, schema_path: Path, split: str, propensity_dir: Path,
                  scored: list[tuple[str, np.ndarray]], label: str) -> str:
    """승패·p가 없으면(C1 이전 export, 행동 모델 미실행) 이유를 적고 건너뛴다."""
    propensity_path = propensity_dir / f"propensity-{split}.jsonl"
    if not propensity_path.exists():
        return f"V 생략: `{propensity_path}`가 없다. `python -m dfgg_ltr.propensity --split {split}`을 먼저 돌린다.\n"
    test_set = load_dataset(data_path, schema_path, split, "test")
    meta = load_query_meta(data_path, split, "test")
    if np.isnan(meta.win).all():
        return "V 생략: export에 `win`이 없다. C1 이후 export로 다시 내보낸다.\n"
    reference = scored[0][0]
    rows = value_rows(test_set, meta, load_propensities(propensity_path, test_set.qids), scored, reference)
    return render_value_report(f"{split} split{label}", rows, reference)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--schema", type=Path, default=Path("data/feature_schema.json"))
    parser.add_argument("--rounds", type=int, default=500)
    parser.add_argument("--label", default="")
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--propensity-dir", type=Path, default=None,
                        help="propensity-<split>.jsonl이 있는 폴더. 주면 승률 지표 V를 함께 보고한다")
    parser.add_argument("--reference-model", type=Path, default=None,
                        help="비교 기준으로 쓸 내보낸 모델 JSON(현재 운영 모델). V 차이의 기준이 된다")
    args = parser.parse_args()
    reference_model = None
    if args.reference_model is not None:
        reference_model = json.loads(args.reference_model.read_text(encoding="utf-8"))

    baselines = ["build_score", "champion_base_rate_all", "self_synergy_score",
                 "ally_synergy_score", "counter_score", "random"]

    sections = []
    for split in ("game", "patch"):
        rows, info, scored = evaluate_split(args.data, args.schema, split, args.rounds, baselines, reference_model)
        sections.append(render_report(f"{split} split{args.label}", rows))
        if args.propensity_dir is not None:
            sections.append(value_section(args.data, args.schema, split, args.propensity_dir, scored, args.label))
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
