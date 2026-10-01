"""학습 데이터(JSONL)를 LightGBM이 요구하는 형태로 적재한다.

feature 계산은 여기서 하지 않는다. Java가 서빙과 동일한 코드로 뽑아 놓은 벡터를 그대로 쓴다 —
Python에 두 번째 구현이 생기면 학습과 서빙이 서서히 어긋나고, 그 어긋남은 오프라인 지표로
드러나지 않는다.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np

VALID_SPLITS = ("game", "patch")
VALID_SUBSETS = ("train", "test")


@dataclass(frozen=True)
class Dataset:
    """LightGBM lambdarank 입력 한 벌.

    group은 qid별 행 수의 목록이고 features의 행 순서와 대응해야 한다 —
    순서가 어긋나면 모델이 엉뚱한 묶음 안에서 순위를 배우는데 지표로는 잘 드러나지 않는다.
    """

    features: np.ndarray
    labels: np.ndarray
    group: np.ndarray
    qids: list[str]
    feature_names: list[str]
    schema_fingerprint: str


def load_dataset(data_path: Path, schema_path: Path, split: str, subset: str) -> Dataset:
    if split not in VALID_SPLITS:
        raise ValueError(f"split은 {VALID_SPLITS} 중 하나여야 합니다: {split}")
    if subset not in VALID_SUBSETS:
        raise ValueError(f"subset은 {VALID_SUBSETS} 중 하나여야 합니다: {subset}")

    schema = json.loads(Path(schema_path).read_text(encoding="utf-8"))
    feature_names: list[str] = schema["feature_names"]
    split_field = f"split_{split}"

    # qid별로 모은다. group은 연속된 행이어야 하므로 파일 순서에 기대지 않는다.
    rows_by_qid: dict[str, list[tuple[int, list[float]]]] = {}
    with Path(data_path).open(encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)
            if row[split_field] != subset:
                continue
            features = row["features"]
            if len(features) != len(feature_names):
                raise ValueError(
                    f"feature 길이가 스키마와 다릅니다: {len(features)} != {len(feature_names)} "
                    f"(qid={row['qid']}). 스키마를 바꿨다면 학습 데이터를 다시 export해야 합니다."
                )
            rows_by_qid.setdefault(row["qid"], []).append((row["label"], features))

    qids = list(rows_by_qid.keys())
    labels: list[int] = []
    features: list[list[float]] = []
    group: list[int] = []
    for qid in qids:
        rows = rows_by_qid[qid]
        group.append(len(rows))
        for label, vector in rows:
            labels.append(label)
            # JSON의 null → NaN. 0으로 바꾸면 "데이터 없음"과 "값이 0"이 뒤섞인다.
            features.append([np.nan if value is None else float(value) for value in vector])

    return Dataset(
        features=np.asarray(features, dtype=np.float64),
        labels=np.asarray(labels, dtype=np.int32),
        group=np.asarray(group, dtype=np.int32),
        qids=qids,
        feature_names=feature_names,
        schema_fingerprint=schema["schema_fingerprint"],
    )
