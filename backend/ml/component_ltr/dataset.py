"""Java JSONL의 계약을 검증하고 Query별 연속 후보 배열을 만든다."""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

import numpy as np
from dfgg_ltr.dataset import Dataset
from component_ltr.feature_schema import FEATURE_NAMES, SCHEMA_VERSION, SCHEMA_FINGERPRINT


class DatasetValidationError(ValueError):
    """실패 건수와 위치를 유지한다. 오류 행을 버리고 학습을 계속하지 않는다."""
    def __init__(self, report):
        self.report = report
        super().__init__("학습 데이터 검증 실패 (정답·후보·계약): " + json.dumps(report, ensure_ascii=False))


def validate_row(row, seen):
    """한 Query의 불변식을 검증한다. 오류 분류는 첫 실패 규칙 기준이다."""
    if not isinstance(row, dict):
        raise ValueError("ROW_FORMAT")
    if row["schemaVersion"] != SCHEMA_VERSION:
        raise ValueError("SCHEMA_VERSION")
    if row["featureNames"] != FEATURE_NAMES:
        raise ValueError("FEATURE_ORDER")
    if row["targetSource"] != "OBSERVED_FUTURE_TARGET_ITEM":
        raise ValueError("TARGET_SOURCE")
    if row["status"] != "READY":
        raise ValueError("NOT_READY")
    if type(row["targetItemId"]) is not int or row["targetItemId"] <= 0:
        raise ValueError("TARGET_ITEM")
    qid = f"{row['matchId']}:{row['participantId']}:{row['startPurchaseOrder']}:{row['endPurchaseOrder']}:{row['targetItemId']}"
    if qid in seen:
        raise ValueError("DUPLICATE_QUERY")
    seen.add(qid)
    candidates = row["candidates"]
    if not isinstance(candidates, list) or len(candidates) < 2:
        raise ValueError("QUERY_SIZE")
    if any(type(c["label"]) is not int or c["label"] not in (0, 1) for c in candidates):
        raise ValueError("LABEL")
    if sum(c["label"] == 1 for c in candidates) != 1:
        raise ValueError("POSITIVE_COUNT")
    quantities = [tuple(sorted(c["itemQuantities"].items())) for c in candidates]
    if len(set(quantities)) != len(quantities):
        raise ValueError("DUPLICATE_CANDIDATE")
    for candidate in candidates:
        if (not candidate["itemQuantities"]
                or any(type(v) is not int or v <= 0 for v in candidate["itemQuantities"].values())):
            raise ValueError("ITEM_QUANTITY")
        vector = candidate["features"]
        if not isinstance(vector, list) or len(vector) != len(FEATURE_NAMES):
            raise ValueError("FEATURE_COUNT")
        if any(type(v) not in (int, float) or not np.isfinite(v) for v in vector):
            raise ValueError("FEATURE_NUMERIC")
        if candidate["label"] == 1 and candidate["itemQuantities"] != row["observedItems"]:
            raise ValueError("POSITIVE_ITEMS")
    return qid


def load_dataset(path: Path, report=None) -> Dataset:
    """전 행을 검사해 사유별 오류를 집계한다. 한 행이라도 오류가 있으면 전체 학습을 중단한다."""
    features, labels, groups, qids = [], [], [], []
    seen, reasons, examples = set(), Counter(), []
    rows = 0
    with path.open(encoding="utf-8") as source:
        for rows, line in enumerate(source, 1):
            try:
                row = json.loads(line)
                qid = validate_row(row, seen)
            except (ValueError, KeyError, TypeError, AttributeError, OverflowError) as error:
                reason = ("JSON_PARSE" if isinstance(error, json.JSONDecodeError)
                          else "MISSING_FIELD" if isinstance(error, KeyError)
                          else str(error) if type(error) is ValueError else "ROW_FORMAT")
                reasons[reason] += 1
                if len(examples) < 20:
                    examples.append({"line": rows, "reason": reason})
                continue
            for candidate in row["candidates"]:
                features.append(candidate["features"])
                labels.append(candidate["label"])
            groups.append(len(row["candidates"]))
            qids.append(qid)
    if rows == 0:
        reasons["EMPTY_DATASET"] += 1
    summary = {"path": str(path), "rows": rows, "validGroups": len(groups),
               "invalidGroups": sum(reasons.values()), "excludedGroups": 0,
               "errorCounts": dict(reasons), "errorExamples": examples}
    if report is not None:
        report.update(summary)
    if reasons:
        raise DatasetValidationError(summary)
    return Dataset(np.asarray(features, dtype=np.float64), np.asarray(labels, dtype=np.int32),
                   np.asarray(groups, dtype=np.int32), qids, FEATURE_NAMES.copy(), SCHEMA_FINGERPRINT)


def load_splits(directory: Path, report_path: Path):
    """모든 분할과 저장된 manifest를 검증한 뒤 학습으로 넘긴다."""
    datasets, reports = {}, {}
    errors = []
    for split in ("train", "validation", "test"):
        reports[split] = {}
        try:
            datasets[split] = load_dataset(directory / f"{split}.jsonl", reports[split])
        except DatasetValidationError as error:
            errors.append(str(error))
    split_errors = []
    if not errors:
        matches = {s: {qid.split(":")[0] for qid in d.qids} for s, d in datasets.items()}
        for a, b in (("train", "validation"), ("train", "test"), ("validation", "test")):
            overlap = matches[a] & matches[b]
            if overlap:
                split_errors.append({"reason": "MATCH_OVERLAP", "splits": [a, b], "count": len(overlap),
                                     "examples": sorted(overlap)[:20]})
        manifest = json.loads((directory / "manifest.json").read_text())
        for split, dataset in datasets.items():
            counts = Counter(qid.split(":")[0] for qid in dataset.qids)
            for match_id, count in counts.items():
                entry = manifest["matches"].get(match_id)
                if entry is None or entry.get("split") != split or entry.get("rankingGroups") != count:
                    split_errors.append({"reason": "MANIFEST_MISMATCH", "matchId": match_id, "split": split})
            expected = {m for m, e in manifest["matches"].items()
                        if e["split"] == split and e["rankingGroups"] > 0}
            if expected != set(counts):
                split_errors.append({"reason": "MANIFEST_MISSING_GROUPS", "split": split})
    summary = {"splits": reports, "splitErrors": split_errors, "passed": not errors and not split_errors}
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    if not summary["passed"]:
        raise ValueError("학습 데이터 검증 실패: " + str(report_path))
    return datasets["train"], datasets["validation"], datasets["test"]
