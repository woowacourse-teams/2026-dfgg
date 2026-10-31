"""두 모델을 같은 문제 위에서 비교한다.

후보 집합이 다르면 NDCG를 직접 비교할 수 없다 — 쉬운 오답이 빠지면 지표는 떨어지는데
추천이 나빠진 것은 아니다. 그래서 같은 test 행을 두 모델로 각각 채점한다.
"""
from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

import lightgbm as lgb
import numpy as np

from dfgg_ltr.metrics import evaluate_ranking

SCHEMA = json.loads(Path("data/feature_schema.json").read_text(encoding="utf-8"))
GROUPS = defaultdict(list)
for index, name in enumerate(SCHEMA["feature_groups"]):
    GROUPS[name].append(index)



def load_test(path: str):
    by_qid = defaultdict(list)
    with Path(path).open(encoding="utf-8") as file:
        for line in file:
            row = json.loads(line)
            if row["split_game"] == "test":
                by_qid[row["qid"]].append(row)
    features, labels, group = [], [], []
    for qid in sorted(by_qid):
        rows = by_qid[qid]
        group.append(len(rows))
        for row in rows:
            labels.append(row["label"])
            features.append([np.nan if v is None else float(v) for v in row["features"]])
    return (np.asarray(features), np.asarray(labels, np.int32), np.asarray(group, np.int32))


SRC = {"COUNTER": SCHEMA["feature_names"].index("source_counter"),
       "ALLY_SYNERGY": SCHEMA["feature_names"].index("source_ally_synergy")}

models = {
    "B counter하한만": lgb.Booster(model_file="data/booster_floor1.txt"),
    "F7 ally lift":   lgb.Booster(model_file="data/booster_f7.txt"),
}
sets = {
    "B의 test  (후보 10.2)": load_test("data/train_floor1.jsonl"),
    "F7의 test (후보 11.8)": load_test("data/train_f7.jsonl"),
}

print("같은 test 세트를 두 모델로 각각 채점\n")
for set_name, (features, labels, group) in sets.items():
    print(f"── {set_name}  query {len(group):,} / 행 {len(labels):,}")
    for model_name, booster in models.items():
        m = evaluate_ranking(labels, booster.predict(features, raw_score=True), group, [1, 3, 5])
        print(f"   {model_name}  NDCG@1 {m['ndcg@1']:.4f}  @3 {m['ndcg@3']:.4f}"
              f"  @5 {m['ndcg@5']:.4f}  MRR {m['mrr']:.4f}")
    print()

print("묶음 기여 부호 — 소속/비소속")
print(f"{'':<12}{'세트':<24}{'소속 비율':>10}{'소속 기여':>11}{'비소속 기여':>12}")
print("-" * 72)
for group_name in ["COUNTER", "ALLY_SYNERGY"]:
    cols = GROUPS[group_name]
    src = SRC[group_name]
    print(f"  [{group_name}]")
    for set_name, (features, labels, group) in sets.items():
        member = features[:, src] > 0
        for model_name, booster in models.items():
            contrib = booster.predict(features, pred_contrib=True)[:, :-1]
            grouped = contrib[:, cols].sum(axis=1)
            print(f"  {model_name:<16}{set_name:<24}{member.mean() * 100:>8.1f}%"
                  f"{grouped[member].mean():>11.4f}{grouped[~member].mean():>12.4f}")
