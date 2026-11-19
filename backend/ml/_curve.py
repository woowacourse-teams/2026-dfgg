"""학습 데이터 query 수를 늘리면 지표가 얼마나 오르는가 — 학습 곡선.

test 세트는 고정하고 train query 수만 바꾼다. 곡선이 이미 평평하면 export를 늘려도
지표가 안 오르므로, 4시간짜리 export를 돌릴 근거가 없다.
"""
from __future__ import annotations

import json
import random
from collections import defaultdict
from pathlib import Path

import lightgbm as lgb
import numpy as np

from dfgg_ltr.metrics import evaluate_ranking
from dfgg_ltr.train import PARAMS, to_lgb_dataset
from dfgg_ltr.dataset import Dataset

SCHEMA = json.loads(Path("data/feature_schema.json").read_text(encoding="utf-8"))
NAMES = SCHEMA["feature_names"]
SIZES = [2_000, 4_000, 8_000, 12_000, 16_000, 20_000, 23_800]
ROUNDS = 500
SEED = 17

by_qid_train, by_qid_test = defaultdict(list), defaultdict(list)
with Path("data/train_noleak.jsonl").open(encoding="utf-8") as file:
    for line in file:
        row = json.loads(line)
        (by_qid_train if row["split_game"] == "train" else by_qid_test)[row["qid"]].append(row)

print(f"train query {len(by_qid_train):,} / test query {len(by_qid_test):,}")


def build(by_qid, qids) -> Dataset:
    features, labels, group = [], [], []
    for qid in qids:
        rows = by_qid[qid]
        group.append(len(rows))
        for row in rows:
            labels.append(row["label"])
            features.append([np.nan if v is None else float(v) for v in row["features"]])
    return Dataset(np.asarray(features), np.asarray(labels, np.int32),
                   np.asarray(group, np.int32), list(qids), NAMES, SCHEMA["schema_fingerprint"])


test_qids = sorted(by_qid_test)
test_set = build(by_qid_test, test_qids)
print(f"고정 test 행 {len(test_set.labels):,}\n")

all_train = sorted(by_qid_train)
random.Random(SEED).shuffle(all_train)

print(f"{'train query':>12}{'행':>10}{'NDCG@1':>9}{'NDCG@3':>9}{'NDCG@5':>9}{'MRR':>8}{'트리':>7}")
print("-" * 66)
previous = None
for size in SIZES:
    if size > len(all_train):
        break
    subset = all_train[:size]
    # 검증셋은 train 안에서 떼어 test를 오염시키지 않는다.
    cut = int(len(subset) * 0.9)
    train_set = build(by_qid_train, subset[:cut])
    valid_set = build(by_qid_train, subset[cut:])

    booster = lgb.train(
        PARAMS, to_lgb_dataset(train_set), num_boost_round=ROUNDS,
        valid_sets=[to_lgb_dataset(valid_set, reference=to_lgb_dataset(train_set))],
        callbacks=[lgb.early_stopping(50, verbose=False)],
    )
    scores = booster.predict(test_set.features, raw_score=True)
    metrics = evaluate_ranking(test_set.labels, scores, test_set.group, [1, 3, 5])
    line = (f"{size:>12,}{len(train_set.labels):>10,}"
            f"{metrics['ndcg@1']:>9.4f}{metrics['ndcg@3']:>9.4f}{metrics['ndcg@5']:>9.4f}"
            f"{metrics['mrr']:>8.4f}{booster.num_trees():>7}")
    if previous is not None:
        line += f"   NDCG@5 {metrics['ndcg@5'] - previous:+.4f}"
    previous = metrics["ndcg@5"]
    print(line)
