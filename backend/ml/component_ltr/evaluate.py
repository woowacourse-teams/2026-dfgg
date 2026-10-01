"""순위 평가: 각 구매 시점의 정답 순위를 지표로 계산한다.

모델 학습이나 파일 저장을 하지 않는다. Core와 달리 Component 정답은 label=1이다.
"""
from __future__ import annotations

import numpy as np
from dfgg_ltr.metrics import ndcg_at_k


def evaluate(dataset, scores) -> dict:
    """구매 시점마다 평가한 뒤 평균한다. Core의 정답 등급 3 대신 Component 등급 1을 찾는다."""
    results = {"hit@1": [], "hit@3": [], "mrr": [], "ndcg@1": [], "ndcg@3": []}
    offset = 0
    for size in dataset.group:
        labels = dataset.labels[offset:offset + size]
        group_scores = scores[offset:offset + size]
        # 동점이면 원래 후보 순서를 유지한다. 모델과 규칙에 같은 동점 정책을 적용한다.
        order = np.argsort(-group_scores, kind="stable")
        rank = int(np.flatnonzero(labels[order] == 1)[0]) + 1
        results["hit@1"].append(float(rank == 1))
        results["hit@3"].append(float(rank <= 3))
        results["mrr"].append(1.0 / rank)
        results["ndcg@1"].append(float(rank == 1))  # 정답 하나인 이진 relevance에서는 Hit@1과 같다.
        results["ndcg@3"].append(ndcg_at_k(labels, group_scores, 3))
        offset += size
    return {name: float(np.mean(values)) for name, values in results.items()}


def evaluate_baselines(dataset) -> dict:
    """동일 후보·동점 정책으로 규칙 기준선을 평가한다. 진행도 기준선은 정의 후 추가한다."""
    column = {name: dataset.feature_names.index(name) for name in
              ("additional_cost", "consumed_owned_quantity")}
    return {"COST_DESC": evaluate(dataset, dataset.features[:, column["additional_cost"]]),
            "CHEAPEST": evaluate(dataset, -dataset.features[:, column["additional_cost"]]),
            "MOST_MATERIAL_CONSUMED": evaluate(dataset, dataset.features[:, column["consumed_owned_quantity"]])}
