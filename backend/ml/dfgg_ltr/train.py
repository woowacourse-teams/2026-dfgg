"""LambdaMART 학습.

feature 계산은 하지 않는다 — Java가 서빙과 동일한 코드로 내보낸 벡터를 그대로 쓴다.

Java 추론(T12)과의 계약:
  * numeric feature만 사용한다. `categorical_feature`와 `linear_tree`를 쓰지 않는다
  * 결측은 NaN 그대로 둔다. LightGBM이 `default_left` 방향으로 분기하고 Java도 그대로 따른다
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import lightgbm as lgb
import numpy as np

from dfgg_ltr.dataset import Dataset, load_dataset
from dfgg_ltr.model_export import to_export_dict

EVAL_AT = [1, 3, 5]

PARAMS = {
    "objective": "lambdarank",
    "metric": "ndcg",
    "ndcg_eval_at": EVAL_AT,
    "learning_rate": 0.05,
    "num_leaves": 63,
    "min_data_in_leaf": 50,
    "feature_fraction": 0.8,
    "bagging_fraction": 0.8,
    "bagging_freq": 1,
    "lambdarank_truncation_level": 20,
    "verbose": -1,
    # Java 추론이 numeric split만 다루므로 범주형 취급을 끈다.
    "feature_pre_filter": False,
}


def to_lgb_dataset(dataset: Dataset, reference: lgb.Dataset | None = None) -> lgb.Dataset:
    return lgb.Dataset(
        dataset.features, label=dataset.labels, group=dataset.group,
        feature_name=dataset.feature_names, categorical_feature=[],
        reference=reference, free_raw_data=False,
    )


def train(train_set: Dataset, valid_set: Dataset, rounds: int) -> tuple[lgb.Booster, dict]:
    lgb_train = to_lgb_dataset(train_set)
    lgb_valid = to_lgb_dataset(valid_set, reference=lgb_train)
    results: dict = {}
    booster = lgb.train(
        PARAMS, lgb_train, num_boost_round=rounds,
        valid_sets=[lgb_valid], valid_names=["valid"],
        callbacks=[
            lgb.early_stopping(50, verbose=False),
            lgb.record_evaluation(results),
            lgb.log_evaluation(period=50),
        ],
    )
    return booster, results


def describe(name: str, dataset: Dataset) -> str:
    labels, counts = np.unique(dataset.labels, return_counts=True)
    distribution = ", ".join(f"{int(l)}:{c}" for l, c in zip(labels, counts))
    return (f"{name}: query {len(dataset.group):,} / 행 {len(dataset.labels):,} "
            f"/ 라벨 {{{distribution}}}")


def main() -> None:
    parser = argparse.ArgumentParser(description="dfgg LTR LambdaMART 학습")
    parser.add_argument("--data", type=Path, default=Path("data/train.jsonl"))
    parser.add_argument("--schema", type=Path, default=Path("data/feature_schema.json"))
    parser.add_argument("--split", choices=["game", "patch"], default="game")
    parser.add_argument("--rounds", type=int, default=500)
    parser.add_argument("--out", type=Path, default=None,
                        help="모델 JSON 출력 경로. 주지 않으면 학습만 하고 내보내지 않는다")
    args = parser.parse_args()

    train_set = load_dataset(args.data, args.schema, args.split, "train")
    valid_set = load_dataset(args.data, args.schema, args.split, "test")
    print(f"\n=== split={args.split} ===")
    print(describe("train", train_set))
    print(describe("valid", valid_set))

    booster, results = train(train_set, valid_set, args.rounds)

    print(f"\n최적 반복: {booster.best_iteration}")
    for k in EVAL_AT:
        key = f"ndcg@{k}"
        if key in results["valid"]:
            print(f"  NDCG@{k} = {results['valid'][key][booster.best_iteration - 1]:.4f}")

    if args.out is not None:
        exported = to_export_dict(booster, train_set.feature_names, train_set.schema_fingerprint)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(exported, allow_nan=False), encoding="utf-8")
        size_mb = args.out.stat().st_size / 1e6
        print(f"\n모델 저장: {args.out} ({size_mb:.1f}MB, 트리 {len(exported['trees'])}개)")


if __name__ == "__main__":
    main()
