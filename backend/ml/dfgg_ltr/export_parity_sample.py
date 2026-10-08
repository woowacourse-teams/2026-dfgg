"""Java parity 테스트용 fixture를 만든다.

Java가 로드하는 것과 같은 `model.json`에서, 등가성이 증명된 참조 구현으로 기대 점수를 계산한다.
그래서 Java 쪽이 어긋나면 원인이 모델 형식이 아니라 Java 순회로 좁혀진다.

사용:
    uv run python -m dfgg_ltr.export_parity_sample \
        --model ../dfgg/src/main/resources/ltr/model.json \
        --data data/train.jsonl \
        --out ../dfgg/src/test/resources/ltr/parity_sample.json
"""
from __future__ import annotations

import argparse
import json
import math
import random
from pathlib import Path
from typing import Sequence

from dfgg_ltr.reference_predict import predict_with_flat_trees


def threshold_boundary_rows(model: dict) -> list[list[float | None]]:
    """각 feature의 임계값을 정확히 때리는 행들을 만든다.

    실데이터 표본만으로는 `<=`의 경계가 밟히지 않아, `<`로 잘못 구현해도 parity가 통과한다
    (변이 테스트로 확인한 실제 구멍이다). 모델이 실제로 쓰는 임계값을 그대로 값으로 넣어
    등호 경계를 강제한다. 분기에 쓰이지 않는 feature는 임의값 대신 결측으로 둔다.
    """
    feature_count = len(model["feature_names"])
    thresholds: dict[int, list[float]] = {}
    for tree in model["trees"]:
        for feature_index, threshold in zip(tree["split_feature"], tree["threshold"]):
            thresholds.setdefault(feature_index, []).append(float(threshold))

    row_count = max((len(values) for values in thresholds.values()), default=0)
    rows: list[list[float | None]] = []
    for offset in range(row_count):
        row: list[float | None] = [None] * feature_count
        for feature_index, values in thresholds.items():
            row[feature_index] = values[offset % len(values)]
        rows.append(row)
    return rows


def build_parity_sample(
        model: dict,
        rows: Sequence[Sequence[float | None]],
        boundary_case_count: int = 0,
) -> dict:
    feature_count = len(model["feature_names"])
    cases = []
    for row in rows:
        if len(row) != feature_count:
            raise ValueError(
                f"행의 길이가 스키마와 다릅니다: 행={len(row)}, 스키마={feature_count}")
        numeric = [math.nan if value is None else float(value) for value in row]
        cases.append({
            "features": [None if value is None else float(value) for value in row],
            "expected_score": predict_with_flat_trees(model["trees"], numeric),
        })
    return {
        "schema_fingerprint": model["schema_fingerprint"],
        "feature_names": list(model["feature_names"]),
        "boundary_case_count": boundary_case_count,
        "cases": cases,
    }


def _sample_rows(data_path: Path, count: int, seed: int) -> list[list[float | None]]:
    """실제 학습 데이터에서 뽑는다 — 결측 패턴이 진짜여야 default_left 경로가 실제로 밟힌다."""
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
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--seed", type=int, default=12345)
    args = parser.parse_args()

    model = json.loads(args.model.read_text(encoding="utf-8"))
    feature_count = len(model["feature_names"])

    rows = _sample_rows(args.data, args.count, args.seed)
    # 극단 두 개를 손으로 덧붙인다: 전부 결측(default_left 경로만), 전부 0(경계값 근처)
    rows.append([None] * feature_count)
    rows.append([0.0] * feature_count)

    # 등호 경계를 정확히 때리는 행들을 뒤에 붙인다
    boundary = threshold_boundary_rows(model)
    rows.extend(boundary)

    sample = build_parity_sample(model, rows, boundary_case_count=len(boundary))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(sample, allow_nan=False, indent=1), encoding="utf-8")

    missing = sum(1 for case in sample["cases"] for value in case["features"] if value is None)
    total = len(sample["cases"]) * feature_count
    print(f"parity fixture 저장: {args.out}")
    print(f"  케이스 {len(sample['cases'])}개 (경계 {len(boundary)}개), 결측 비율 {missing / total:.1%}")


if __name__ == "__main__":
    main()
