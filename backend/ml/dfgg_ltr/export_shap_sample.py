"""Java TreeSHAP 검증용 기준값(fixture)을 만든다.

기준은 참조 구현이 아니라 **LightGBM 자체**(`pred_contrib=True`)다. TreeSHAP을 Python과 Java에
두 번 구현하면 둘 다 같은 방식으로 틀릴 위험이 있어, 학습 때 저장해 둔 네이티브 모델을
그대로 기준으로 쓴다.

사용:
    uv run python -m dfgg_ltr.export_shap_sample \
        --booster data/booster.txt --data data/train_noleak.jsonl \
        --schema data/feature_schema.json \
        --out ../dfgg/src/test/resources/ltr/shap_parity_sample.json
"""
from __future__ import annotations

import argparse
import json
import math
import random
from pathlib import Path
from typing import Sequence

import lightgbm as lgb
import numpy as np


def build_shap_parity_sample(
        booster: lgb.Booster,
        rows: Sequence[Sequence[float | None]],
        fingerprint: str,
) -> dict:
    feature_count = booster.num_feature()
    for row in rows:
        if len(row) != feature_count:
            raise ValueError(
                f"행의 길이가 모델과 다릅니다: 행={len(row)}, 모델={feature_count}")

    matrix = np.array(
        [[math.nan if value is None else float(value) for value in row] for row in rows],
        dtype=float)
    # 마지막 열은 base value(기대값)다. 기여도 전체의 합이 raw 예측과 같아진다.
    contributions = booster.predict(matrix, pred_contrib=True)
    scores = booster.predict(matrix, raw_score=True)

    cases = []
    for index, row in enumerate(rows):
        cases.append({
            "features": [None if value is None else float(value) for value in row],
            "expected_contributions": [float(value) for value in contributions[index]],
            "expected_score": float(scores[index]),
        })
    return {
        "schema_fingerprint": fingerprint,
        "feature_count": feature_count,
        "cases": cases,
    }


def _sample_rows(data_path: Path, count: int, seed: int) -> list[list[float | None]]:
    rng = random.Random(seed)
    reservoir: list[list[float | None]] = []
    with data_path.open(encoding="utf-8") as file:
        for seen, line in enumerate(file):
            row = json.loads(line)["features"]
            if len(reservoir) < count:
                reservoir.append(row)
            else:
                index = rng.randrange(seen + 1)
                if index < count:
                    reservoir[index] = row
    return reservoir


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--booster", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--schema", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--seed", type=int, default=4242)
    args = parser.parse_args()

    booster = lgb.Booster(model_file=str(args.booster))
    fingerprint = json.loads(args.schema.read_text(encoding="utf-8"))["schema_fingerprint"]

    rows = _sample_rows(args.data, args.count, args.seed)
    rows.append([None] * booster.num_feature())   # 전부 결측
    rows.append([0.0] * booster.num_feature())    # 경계 근처

    sample = build_shap_parity_sample(booster, rows, fingerprint)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(sample, allow_nan=False, indent=1), encoding="utf-8")
    print(f"SHAP parity fixture 저장: {args.out} (케이스 {len(sample['cases'])}개)")


if __name__ == "__main__":
    main()
