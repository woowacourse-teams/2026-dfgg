"""검증 분석: 어디서 틀리는지, 현재 Feature로 구분할 수 있는지 조사한다.

설정 선택용 분석은 validation만 사용한다. test 지표는 evaluate로 따로 계산한다.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from component_ltr.evaluate import evaluate, evaluate_baselines


def diagnostics(dataset, scores, path: Path) -> dict:
    """고도화 판단용 검증 보고서. 평가(test) 오답을 학습 설정 선택에 사용하지 않는다."""
    positions = {}
    wrong_examples = []
    ambiguous = 0
    offset = 0
    with path.open(encoding="utf-8") as source:
        for qid, size, line in zip(dataset.qids, dataset.group, source, strict=True):
            row = json.loads(line)
            group_scores = scores[offset:offset + size]
            labels = dataset.labels[offset:offset + size]
            vectors = dataset.features[offset:offset + size]
            positive = int(np.flatnonzero(labels == 1)[0])
            selected = int(np.argmax(group_scores))
            # 정답과 다른 후보의 벡터가 같으면 현재 Feature만으로 둘을 구분할 수 없다.
            same = np.all(vectors == vectors[positive], axis=1)
            ambiguous += int(np.count_nonzero(same) > 1)
            bucket = positions.setdefault(row["position"] or "UNKNOWN", {"groups": 0, "correct": 0})
            bucket["groups"] += 1
            bucket["correct"] += int(selected == positive)
            if selected != positive and len(wrong_examples) < 10:
                wrong_examples.append({"qid": qid, "targetCoreId": row["targetCoreId"],
                                       "observedItems": row["observedItems"],
                                       "selectedItems": row["candidates"][selected]["itemQuantities"],
                                       "positiveFeatureCollision": bool(np.count_nonzero(same) > 1)})
            offset += size
    for bucket in positions.values():
        bucket["hit@1"] = bucket["correct"] / bucket["groups"]
    return {"subset": "validation", "positions": positions,
            "positiveFeatureCollisionGroups": ambiguous, "wrongExamples": wrong_examples}


def validation_report(booster, dataset, data_path: Path, history: dict) -> dict:
    """오답 분석·모델/기준선 지표·학습 이력·분할 중요도를 한 보고서로 조립한다."""
    scores = booster.predict(dataset.features)
    report = diagnostics(dataset, scores, data_path)
    report["model"] = evaluate(dataset, scores)
    report["cost_desc_baseline"] = evaluate(dataset, dataset.features[:, 0])
    report["baselines"] = evaluate_baselines(dataset)
    report["history"] = history
    # gain은 분할 기여도이며 인과적 중요도나 Feature 제거 실험 결과가 아니다.
    report["feature_gain"] = dict(zip(dataset.feature_names,
                                    booster.feature_importance(importance_type="gain").tolist()))
    return report
