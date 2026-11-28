"""counter/ally evidence가 응답에 채워질 비율의 상한을 잰다.

JSONL에는 적별 lift가 없어 CounterEvidence의 `lift > 1.0` 조건은 적용하지 못한다.
그래서 이 값은 상한이다 — 실제 응답은 이보다 같거나 적다.
"""
from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

import lightgbm as lgb
import numpy as np

TOP_K, MIN_SHARE, MAX_HIGHLIGHTS = 5, 0.10, 2
SCHEMA = json.loads(Path("data/feature_schema.json").read_text(encoding="utf-8"))
GROUPS = defaultdict(list)
for i, name in enumerate(SCHEMA["feature_groups"]):
    GROUPS[name].append(i)
NAMES = sorted(GROUPS)
SRC = {"COUNTER": SCHEMA["feature_names"].index("source_counter"),
       "ALLY_SYNERGY": SCHEMA["feature_names"].index("source_ally_synergy")}


def measure(data_path: str, booster_path: str, label: str) -> None:
    by_qid = defaultdict(list)
    with Path(data_path).open(encoding="utf-8") as file:
        for line in file:
            row = json.loads(line)
            if row["split_game"] == "test":
                by_qid[row["qid"]].append(row)

    booster = lgb.Booster(model_file=booster_path)
    rows, positions = [], []
    for qid in sorted(by_qid):
        c = by_qid[qid]
        f = np.array([[np.nan if v is None else float(v) for v in x["features"]] for x in c])
        for i in np.argsort(-booster.predict(f, raw_score=True))[:TOP_K]:
            rows.append(f[i])
            positions.append(c[i]["position"])
    features = np.asarray(rows)
    positions = np.array(positions)

    contrib = booster.predict(features, pred_contrib=True)[:, :-1]
    grouped = np.column_stack([contrib[:, GROUPS[g]].sum(axis=1) for g in NAMES])
    positive_total = np.where(grouped > 0, grouped, 0.0).sum(axis=1)
    share = np.divide(grouped, positive_total[:, None], out=np.zeros_like(grouped),
                      where=positive_total[:, None] > 0)
    rank = np.where(grouped > 0, grouped, -np.inf).argsort(axis=1)[:, ::-1].argsort(axis=1)

    print(f"\n=== {label} — 추천 칸 {len(features):,} ===")
    print(f"{'묶음':<15}{'소속':>8}{'문턱통과':>10}{'상위2':>8}{'최종(소속∧문턱∧상위2)':>22}")
    print("-" * 66)
    for group in ["COUNTER", "ALLY_SYNERGY"]:
        gi = NAMES.index(group)
        member = features[:, SRC[group]] > 0
        floor = (grouped[:, gi] > 0) & (share[:, gi] >= MIN_SHARE)
        top2 = floor & (rank[:, gi] < MAX_HIGHLIGHTS)
        final = member & top2
        print(f"{group:<15}{member.mean() * 100:>7.1f}%{floor.mean() * 100:>9.1f}%"
              f"{top2.mean() * 100:>7.1f}%{final.mean() * 100:>20.2f}%")

    gi = NAMES.index("COUNTER")
    member = features[:, SRC["COUNTER"]] > 0
    final = member & (grouped[:, gi] > 0) & (share[:, gi] >= MIN_SHARE) & (rank[:, gi] < MAX_HIGHLIGHTS)
    print("\n  COUNTER 최종 — 포지션별")
    for p in ["TOP", "JUNGLE", "MID", "BOTTOM", "SUPPORT"]:
        mask = positions == p
        if mask.any():
            print(f"    {p:<9}{final[mask].mean() * 100:>6.2f}%   (칸 {mask.sum():,})")


measure("data/train_floor0.jsonl", "data/booster_floor0.txt", "A 하한 0%")
measure("data/train_floor1.jsonl", "data/booster_floor1.txt", "B 하한 1%")
