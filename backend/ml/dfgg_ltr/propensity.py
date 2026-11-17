"""행동 모델: 플레이어가 그 상황에서 실제로 산 아이템을 고를 확률 p(a | x).

승률 지표 V가 1/p로 가중하는 값이다. 평가 대상 모델과 분리해 한 번 학습해 두어야 후보끼리 같은 p로 비교된다.
서빙에서 받지 못하는 상황 값(구매 시각, 팀 골드 차이)도 feature로 쓴다. 평가 전용이라 서빙과 상관이 없고,
이 값이 빠지면 "지고 있어서 산 아이템"이 승률 낮은 아이템으로 찍힌다.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import replace
from pathlib import Path

import lightgbm as lgb
import numpy as np

from dfgg_ltr.dataset import Dataset, QueryMeta, load_dataset, load_query_meta
from dfgg_ltr.metrics import GROUND_TRUTH_GRADE
from dfgg_ltr.train import PARAMS, to_lgb_dataset

SITUATION_FEATURES = ("purchase_minute", "team_gold_diff")
BEHAVIOR_SEED = 99
HOLDOUT_BUCKETS = 10
TAU_GRID = np.geomspace(0.05, 5, 60)
CALIBRATION_BINS = 10
LOG_FLOOR = 1e-12


def with_situation(dataset: Dataset, meta: QueryMeta) -> Dataset:
    """query별 상황 값을 그 query의 모든 행에 feature로 붙인다. 모르는 값은 NaN 그대로다."""
    if meta.qids != dataset.qids:
        raise ValueError("메타와 데이터셋의 query 순서가 다릅니다. 같은 파일·split·subset으로 읽어야 합니다.")
    columns = [np.repeat(getattr(meta, name), dataset.group) for name in SITUATION_FEATURES]
    return replace(
        dataset,
        features=np.column_stack([dataset.features, *columns]),
        feature_names=[*dataset.feature_names, *SITUATION_FEATURES],
    )


def select_queries(dataset: Dataset, query_mask: np.ndarray) -> Dataset:
    row_mask = np.repeat(query_mask, dataset.group)
    return replace(
        dataset,
        features=dataset.features[row_mask],
        labels=dataset.labels[row_mask],
        group=dataset.group[query_mask],
        qids=[qid for qid, keep in zip(dataset.qids, query_mask) if keep],
    )


def holdout_queries(match_ids: list[str]) -> np.ndarray:
    """조기 종료·온도 맞춤용 검증 query. 경기 단위로 떼어 같은 경기의 query가 학습과 검증에 흩어지지 않게 한다."""
    return np.array([int(hashlib.md5(match_id.encode()).hexdigest(), 16) % HOLDOUT_BUCKETS == 0
                     for match_id in match_ids])


def _query_bounds(group: np.ndarray) -> np.ndarray:
    return np.concatenate([[0], np.cumsum(group)])


def choice_probabilities(scores: np.ndarray, group: np.ndarray, tau: float) -> np.ndarray:
    """행마다 query 안 softmax(score / τ) 확률."""
    bounds = _query_bounds(group)
    probabilities = np.empty_like(scores, dtype=np.float64)
    for start, end in zip(bounds[:-1], bounds[1:]):
        logits = scores[start:end] / tau
        weights = np.exp(logits - logits.max())
        probabilities[start:end] = weights / weights.sum()
    return probabilities


def ground_truth_probabilities(
        scores: np.ndarray, labels: np.ndarray, group: np.ndarray, tau: float) -> np.ndarray:
    """query마다 실제로 산 아이템(등급 3)의 선택 확률. 정답이 없는 query는 NaN이다."""
    probabilities = choice_probabilities(scores, group, tau)
    bounds = _query_bounds(group)
    result = np.full(len(group), np.nan)
    for index, (start, end) in enumerate(zip(bounds[:-1], bounds[1:])):
        chosen = probabilities[start:end][labels[start:end] == GROUND_TRUTH_GRADE]
        if chosen.size == 1:
            result[index] = chosen[0]
    return result


def mean_log_likelihood(probabilities: np.ndarray) -> float:
    known = probabilities[~np.isnan(probabilities)]
    return float(np.log(np.clip(known, LOG_FLOOR, 1.0)).mean())


def fit_temperature(scores: np.ndarray, labels: np.ndarray, group: np.ndarray) -> tuple[float, float]:
    """정답 평균 로그우도가 가장 큰 τ와 그 로그우도."""
    best_tau, best_likelihood = None, -np.inf
    for tau in TAU_GRID:
        likelihood = mean_log_likelihood(ground_truth_probabilities(scores, labels, group, tau))
        if likelihood > best_likelihood:
            best_tau, best_likelihood = float(tau), likelihood
    return best_tau, best_likelihood


def calibration(scores: np.ndarray, labels: np.ndarray, group: np.ndarray, tau: float,
                bins: int = CALIBRATION_BINS) -> list[dict]:
    """모든 후보를 p 구간(0~1 등간격)으로 나눠 평균 p와 실제 선택률을 낸다. 잘 맞으면 두 값이 같다."""
    probabilities = choice_probabilities(scores, group, tau)
    chosen = labels == GROUND_TRUTH_GRADE
    edges = np.linspace(0.0, 1.0, bins + 1)
    bin_index = np.clip(np.digitize(probabilities, edges[1:-1]), 0, bins - 1)
    table = []
    for index in range(bins):
        in_bin = bin_index == index
        table.append({
            "lower": float(edges[index]),
            "upper": float(edges[index + 1]),
            "count": int(in_bin.sum()),
            "mean_p": _mean_or_none(probabilities[in_bin]),
            "chosen_rate": _mean_or_none(chosen[in_bin]),
        })
    return table


def _mean_or_none(values: np.ndarray) -> float | None:
    if values.size == 0:
        return None
    return float(values.mean())


def train_behavior_model(train_set: Dataset, train_match_ids: list[str],
                         rounds: int = 500, seed: int = BEHAVIOR_SEED) -> tuple[lgb.Booster, float, float]:
    """학습 split 안에서 경기 10%를 검증으로 떼어 조기 종료와 온도를 맞춘다. test split은 보지 않는다."""
    valid_queries = holdout_queries(train_match_ids)
    fit_set = select_queries(train_set, ~valid_queries)
    valid_set = select_queries(train_set, valid_queries)
    params = dict(PARAMS, seed=seed, bagging_seed=seed, feature_fraction_seed=seed)
    lgb_fit = to_lgb_dataset(fit_set)
    booster = lgb.train(
        params, lgb_fit, num_boost_round=rounds,
        valid_sets=[to_lgb_dataset(valid_set, reference=lgb_fit)],
        callbacks=[lgb.early_stopping(50, verbose=False)],
    )
    valid_scores = booster.predict(valid_set.features, num_iteration=booster.best_iteration)
    tau, likelihood = fit_temperature(valid_scores, valid_set.labels, valid_set.group)
    return booster, tau, likelihood


def situation_coverage(meta: QueryMeta) -> dict[str, float]:
    return {name: float(np.mean(~np.isnan(getattr(meta, name)))) for name in SITUATION_FEATURES}


def main() -> None:
    parser = argparse.ArgumentParser(description="승률 지표용 행동 모델(propensity) 학습")
    parser.add_argument("--data", type=Path, default=Path("data/train.jsonl"))
    parser.add_argument("--schema", type=Path, default=Path("data/feature_schema.json"))
    parser.add_argument("--split", choices=["game", "patch"], default="game")
    parser.add_argument("--rounds", type=int, default=500)
    parser.add_argument("--out-dir", type=Path, default=Path("data"))
    args = parser.parse_args()

    train_meta = load_query_meta(args.data, args.split, "train")
    test_meta = load_query_meta(args.data, args.split, "test")
    train_set = with_situation(load_dataset(args.data, args.schema, args.split, "train"), train_meta)
    test_set = with_situation(load_dataset(args.data, args.schema, args.split, "test"), test_meta)

    booster, tau, valid_likelihood = train_behavior_model(train_set, train_meta.match_ids, args.rounds)
    test_scores = booster.predict(test_set.features, num_iteration=booster.best_iteration)
    propensities = ground_truth_probabilities(test_scores, test_set.labels, test_set.group, tau)

    report = {
        "split": args.split,
        "tau": tau,
        "best_iteration": booster.best_iteration,
        "valid_log_likelihood": valid_likelihood,
        "test_log_likelihood": mean_log_likelihood(propensities),
        "test_queries": len(test_set.group),
        "propensity_median": float(np.nanmedian(propensities)),
        "situation_coverage_test": situation_coverage(test_meta),
        "calibration_test": calibration(test_scores, test_set.labels, test_set.group, tau),
    }
    args.out_dir.mkdir(parents=True, exist_ok=True)
    propensity_path = args.out_dir / f"propensity-{args.split}.jsonl"
    with propensity_path.open("w", encoding="utf-8") as out:
        for qid, propensity in zip(test_set.qids, propensities):
            out.write(json.dumps({"qid": qid, "propensity": _none_if_nan(propensity)}) + "\n")
    report_path = args.out_dir / f"propensity-{args.split}-report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"[{args.split}] τ={tau:.3f} valid loglik={valid_likelihood:.4f} "
          f"test loglik={report['test_log_likelihood']:.4f} test query={report['test_queries']:,} "
          f"p 중앙값={report['propensity_median']:.3f} 상황 채움={report['situation_coverage_test']}")
    print(f"저장: {propensity_path}, {report_path}")


def _none_if_nan(value: float) -> float | None:
    if np.isnan(value):
        return None
    return float(value)


if __name__ == "__main__":
    main()
