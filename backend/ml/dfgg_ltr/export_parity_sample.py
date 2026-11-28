"""Java parity 테스트용 fixture를 만든다.

Java가 로드하는 것과 같은 `model.json`에서, 등가성이 증명된 참조 구현으로 기대 점수를 계산한다.
그래서 Java 쪽이 어긋나면 원인이 모델 형식이 아니라 Java 순회로 좁혀진다.

사용:
    uv run python -m dfgg_ltr.export_parity_sample \
        --model ../dfgg/src/main/resources/ltr/model.json \
        --data data/train.jsonl \
        --out ../dfgg/src/test/resources/ltr/parity_sample.json

저장소 밖의 모델은 `--model`과 `--out`에 저장소 밖 경로를 주고, 만든 fixture는 Java의
`ExternalCoreModelParityTest`에 넘긴다. 스키마의 앞쪽 feature(기준 계약)로 학습한 모델은 학습 줄보다
짧은데, 그때는 학습 줄을 모델의 칸 수까지만 쓴다(`rows_for_model`).
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


def rows_for_model(rows: Sequence[Sequence[float | None]], model: dict,
                   schema_feature_names: Sequence[str] | None) -> list[list[float | None]]:
    """학습 줄이 모델보다 길면 모델이 본 앞쪽 칸까지만 남긴다.

    스키마 뒤에 feature가 더해진 뒤에도 운영 모델은 앞쪽 feature(기준 계약)로 학습한다. 그 모델의 fixture를
    만들려면 같은 학습 데이터에서 앞쪽 칸만 써야 한다. 앞쪽을 자르는 것이라 칸 번호가 그대로다.

    모델의 feature가 스키마의 앞쪽과 같을 때만 자른다. 중간 feature를 뺀 모델에 앞쪽 칸을 주면 엉뚱한 값으로
    기대 점수가 만들어지고, 그 fixture는 Java가 틀렸을 때도 통과한다.
    """
    count = len(model["feature_names"])
    if all(len(row) == count for row in rows):
        return [list(row) for row in rows]
    if schema_feature_names is None or list(schema_feature_names[:count]) != list(model["feature_names"]):
        raise ValueError(
            "학습 줄의 길이가 모델과 다르고, 모델의 feature가 스키마의 앞쪽과 같은지 확인할 수 없습니다. "
            "--schema로 학습 데이터의 feature_schema.json을 주고, 모델이 그 스키마의 앞쪽 feature로 "
            "학습됐는지 확인하세요.")
    return [list(row[:count]) for row in rows]


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
    parser.add_argument("--schema", type=Path, default=None,
                        help="학습 데이터의 feature_schema.json. 주지 않으면 학습 데이터 옆에서 찾는다. "
                             "모델이 학습 줄보다 짧을 때(기준 계약 모델) 앞쪽 칸만 써도 되는지 확인하는 데 쓴다")
    args = parser.parse_args()

    model = json.loads(args.model.read_text(encoding="utf-8"))
    feature_count = len(model["feature_names"])
    schema_path = args.schema or args.data.parent / "feature_schema.json"
    schema_feature_names = (json.loads(schema_path.read_text(encoding="utf-8"))["feature_names"]
                            if schema_path.exists() else None)

    rows = rows_for_model(_sample_rows(args.data, args.count, args.seed), model, schema_feature_names)
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
