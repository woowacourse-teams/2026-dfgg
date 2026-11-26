"""승리 확률 모델 Q(x, a): 그 상황에서 그 아이템을 샀을 때 이길 확률.

DR 승률이 IPS 보정 앞에 깔아 두는 직접 추정값이다. 행동 모델처럼 평가 대상과 분리해 한 번 학습해 두어야
후보끼리 같은 Q로 비교된다. 학습은 실제로 산 아이템 행(등급 3)만 쓰고, 정답은 그 경기의 승패다.
"""
from __future__ import annotations

import argparse
import json
from dataclasses import fields, replace
from pathlib import Path

import lightgbm as lgb
import numpy as np

from dfgg_ltr.dataset import Dataset, QueryMeta, load_dataset, load_query_meta
from dfgg_ltr.metrics import GROUND_TRUTH_GRADE
from dfgg_ltr.propensity import holdout_queries, select_queries, with_situation

OUTCOME_SEED = 77
OUTCOME_PARAMS = {
    "objective": "binary",
    "metric": "binary_logloss",
    "learning_rate": 0.05,
    "num_leaves": 31,
    "min_data_in_leaf": 50,
    "feature_fraction": 0.8,
    "bagging_fraction": 0.8,
    "bagging_freq": 1,
    "verbose": -1,
}
LOG_FLOOR = 1e-12


def bought_rows(dataset: Dataset, meta: QueryMeta) -> tuple[np.ndarray, np.ndarray]:
    """query마다 실제로 산 아이템 행의 feature와 그 경기 승패. 정답이 없거나 승패를 모르는 query는 뺀다."""
    bounds = np.concatenate([[0], np.cumsum(dataset.group)])
    features, wins = [], []
    for index, (start, end) in enumerate(zip(bounds[:-1], bounds[1:])):
        chosen = np.flatnonzero(dataset.labels[start:end] == GROUND_TRUTH_GRADE)
        if chosen.size != 1 or np.isnan(meta.win[index]):
            continue
        features.append(dataset.features[start + chosen[0]])
        wins.append(meta.win[index])
    return np.array(features), np.array(wins)


def train_outcome_model(train_set: Dataset, train_meta: QueryMeta, rounds: int = 500,
                        seed: int = OUTCOME_SEED) -> lgb.Booster:
    """학습 split 안에서 경기 10%를 검증으로 떼어 조기 종료한다. test split은 보지 않는다."""
    valid_queries = holdout_queries(train_meta.match_ids)
    fit_x, fit_y = bought_rows(select_queries(train_set, ~valid_queries), _select_meta(train_meta, ~valid_queries))
    valid_x, valid_y = bought_rows(select_queries(train_set, valid_queries), _select_meta(train_meta, valid_queries))
    params = dict(OUTCOME_PARAMS, seed=seed, bagging_seed=seed, feature_fraction_seed=seed)
    fit = lgb.Dataset(fit_x, fit_y)
    return lgb.train(params, fit, num_boost_round=rounds, valid_sets=[lgb.Dataset(valid_x, valid_y, reference=fit)],
                     callbacks=[lgb.early_stopping(50, verbose=False)])


def outcome_scores(booster: lgb.Booster, dataset: Dataset) -> np.ndarray:
    """행마다 Q(x, a). dataset은 with_situation으로 상황 값을 붙인 것이어야 한다."""
    return booster.predict(dataset.features, num_iteration=booster.best_iteration)


def log_loss(probabilities: np.ndarray, wins: np.ndarray) -> float:
    clipped = np.clip(probabilities, LOG_FLOOR, 1 - LOG_FLOOR)
    return float(-np.mean(wins * np.log(clipped) + (1 - wins) * np.log(1 - clipped)))


def _select_meta(meta: QueryMeta, query_mask: np.ndarray) -> QueryMeta:
    values = {}
    for field in fields(meta):
        value = getattr(meta, field.name)
        if isinstance(value, np.ndarray):
            values[field.name] = value[query_mask]
            continue
        values[field.name] = [item for item, keep in zip(value, query_mask) if keep]
    return replace(meta, **values)


def main() -> None:
    parser = argparse.ArgumentParser(description="DR 승률용 승리 확률 모델(Q) 학습")
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

    booster = train_outcome_model(train_set, train_meta, args.rounds)
    test_x, test_y = bought_rows(test_set, test_meta)
    predicted = booster.predict(test_x, num_iteration=booster.best_iteration)
    base_rate = float(test_y.mean())
    report = {
        "split": args.split,
        "best_iteration": booster.best_iteration,
        "test_rows": int(test_y.size),
        "test_win_rate": base_rate,
        "test_log_loss": log_loss(predicted, test_y),
        "test_log_loss_constant": log_loss(np.full_like(test_y, base_rate), test_y),
    }
    args.out_dir.mkdir(parents=True, exist_ok=True)
    model_path = args.out_dir / f"outcome-{args.split}.txt"
    booster.save_model(str(model_path), num_iteration=booster.best_iteration)
    report_path = args.out_dir / f"outcome-{args.split}-report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[{args.split}] test logloss={report['test_log_loss']:.4f} "
          f"(상수 {report['test_log_loss_constant']:.4f}) test 행={report['test_rows']:,}")
    print(f"저장: {model_path}, {report_path}")


if __name__ == "__main__":
    main()
