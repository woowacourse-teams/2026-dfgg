"""학습 데이터(JSONL)를 LightGBM이 요구하는 형태로 적재한다.

feature 계산은 여기서 하지 않는다. Java가 서빙과 동일한 코드로 뽑아 놓은 벡터를 그대로 쓴다 —
Python에 두 번째 구현이 생기면 학습과 서빙이 서서히 어긋나고, 그 어긋남은 오프라인 지표로
드러나지 않는다.
"""
from __future__ import annotations

import dataclasses
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
    #: 행마다 어느 아이템인지. features의 행 순서와 같다.
    item_ids: np.ndarray | None = None
    #: query마다 몇 번째 Core를 고르는 스냅샷인지(0부터). group의 순서와 같다.
    purchase_steps: np.ndarray | None = None
    #: 기준 계약 — 지금 운영 모델이 학습된 앞쪽 feature의 개수와 지문. Java가 내보낸 값을 그대로 쓴다.
    baseline_feature_count: int | None = None
    baseline_schema_fingerprint: str | None = None


def load_dataset(data_path: Path, schema_path: Path, split: str, subset: str) -> Dataset:
    if split not in VALID_SPLITS:
        raise ValueError(f"split은 {VALID_SPLITS} 중 하나여야 합니다: {split}")
    if subset not in VALID_SUBSETS:
        raise ValueError(f"subset은 {VALID_SUBSETS} 중 하나여야 합니다: {subset}")

    schema = json.loads(Path(schema_path).read_text(encoding="utf-8"))
    feature_names: list[str] = schema["feature_names"]
    split_field = f"split_{split}"

    # qid별로 모은다. group은 연속된 행이어야 하므로 파일 순서에 기대지 않는다.
    rows_by_qid: dict[str, list[tuple[int, list[float], int]]] = {}
    purchase_step_by_qid: dict[str, int] = {}
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
            rows_by_qid.setdefault(row["qid"], []).append((row["label"], features, row["item_id"]))
            purchase_step_by_qid[row["qid"]] = row["purchase_step"]

    qids = list(rows_by_qid.keys())
    labels: list[int] = []
    features: list[list[float]] = []
    item_ids: list[int] = []
    group: list[int] = []
    for qid in qids:
        rows = rows_by_qid[qid]
        group.append(len(rows))
        for label, vector, item_id in rows:
            labels.append(label)
            item_ids.append(item_id)
            # JSON의 null → NaN. 0으로 바꾸면 "데이터 없음"과 "값이 0"이 뒤섞인다.
            features.append([np.nan if value is None else float(value) for value in vector])

    return Dataset(
        features=np.asarray(features, dtype=np.float64),
        labels=np.asarray(labels, dtype=np.int32),
        group=np.asarray(group, dtype=np.int32),
        qids=qids,
        feature_names=feature_names,
        schema_fingerprint=schema["schema_fingerprint"],
        item_ids=np.asarray(item_ids, dtype=np.int64),
        purchase_steps=np.asarray([purchase_step_by_qid[qid] for qid in qids], dtype=np.int32),
        # 기준 계약이 없는 옛 스키마 파일도 읽는다. 기준 모델을 만들 때만 필요한 값이다.
        baseline_feature_count=schema.get("baseline_feature_count"),
        baseline_schema_fingerprint=schema.get("baseline_schema_fingerprint"),
    )


def baseline_view(dataset: Dataset) -> Dataset:
    """기준 계약의 feature만 남긴 같은 표본 — 지금 운영 모델과 입력이 같은 모델을 학습할 때 쓴다.

    앞쪽 열만 잘라내므로 열 번호가 Java의 feature 번호와 그대로 같다. 그래서 이 데이터로 학습한 모델은
    기준 지문을 달고 서버의 기준 계약으로 읽힌다. 중간 열을 빼는 `ablation.apply_feature_mask`는 열 번호가
    밀리기 때문에 일부러 서빙할 수 없는 지문을 붙인다 — 그 도구와 섞어 쓰지 않는다.
    """
    if dataset.baseline_feature_count is None or dataset.baseline_schema_fingerprint is None:
        raise ValueError(
            "스키마 파일에 기준 계약(baseline_feature_count, baseline_schema_fingerprint)이 없습니다. "
            "학습 데이터를 다시 export해야 합니다."
        )
    count = dataset.baseline_feature_count
    return dataclasses.replace(
        dataset,
        features=dataset.features[:, :count],
        feature_names=dataset.feature_names[:count],
        schema_fingerprint=dataset.baseline_schema_fingerprint,
    )


@dataclass(frozen=True)
class QueryMeta:
    """query별 승패·상황 메타 값. feature가 아니라 승률 지표(V)와 행동 모델만 쓴다.

    순서는 같은 인자로 부른 `load_dataset`의 `qids`와 같다(파일에서 처음 나온 순서).
    모르는 값은 NaN이다.
    """

    qids: list[str]
    match_ids: list[str]
    patches: list[str]
    win: np.ndarray
    purchase_minute: np.ndarray
    team_gold_diff: np.ndarray
    own_total_gold: np.ndarray


def load_query_meta(data_path: Path, split: str, subset: str) -> QueryMeta:
    if split not in VALID_SPLITS:
        raise ValueError(f"split은 {VALID_SPLITS} 중 하나여야 합니다: {split}")
    if subset not in VALID_SUBSETS:
        raise ValueError(f"subset은 {VALID_SUBSETS} 중 하나여야 합니다: {subset}")
    split_field = f"split_{split}"

    first_rows: dict[str, dict] = {}
    with Path(data_path).open(encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)
            if row[split_field] == subset and row["qid"] not in first_rows:
                first_rows[row["qid"]] = row

    rows = list(first_rows.values())
    return QueryMeta(
        qids=list(first_rows.keys()),
        match_ids=[row["match_id"] for row in rows],
        patches=[row["patch"] for row in rows],
        win=_meta_column(rows, "win"),
        purchase_minute=_meta_column(rows, "purchase_minute"),
        team_gold_diff=_meta_column(rows, "team_gold_diff"),
        own_total_gold=_meta_column(rows, "own_total_gold"),
    )


def _meta_column(rows: list[dict], name: str) -> np.ndarray:
    """칸이 없거나(C1 이전 export) null이면 NaN."""
    return np.asarray([_nan_if_none(row.get(name)) for row in rows], dtype=np.float64)


def _nan_if_none(value) -> float:
    if value is None:
        return np.nan
    return float(value)
