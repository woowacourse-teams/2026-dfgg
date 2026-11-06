"""순위 평가: 각 구매 시점의 관측 구매 순위를 지표로 계산한다.

모델 학습이나 파일 저장을 하지 않는다. Component 관측 구매는 label=1이다.
Hit/MRR/NDCG는 관측 선택 재현 지표이며 구매의 최적성이나 승률을 측정하지 않는다.
"""
from __future__ import annotations

import numpy as np
from dfgg_ltr.metrics import ndcg_at_k


def evaluate(dataset, scores) -> dict:
    """구매 시점마다 평가한 뒤 평균한다. Component의 관측 구매 등급 1을 찾는다."""
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
        results["ndcg@1"].append(float(rank == 1))  # 관측 구매 하나인 이진 relevance에서는 Hit@1과 같다.
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


def stratified_evaluation(dataset, scores, path):
    """같은 모델의 지표만 유형/단계/역할/예산별 집계한다. 분리 학습하지 않는다."""
    import json
    from dfgg_ltr.dataset import Dataset
    buckets = {}
    offset = 0
    with path.open(encoding='utf-8') as source:
        for size, line in zip(dataset.group, source, strict=True):
            row = json.loads(line)
            end = offset + int(size)
            group = Dataset(dataset.features[offset:end], dataset.labels[offset:end],
                            np.asarray([size]), [], dataset.feature_names, dataset.schema_fingerprint)
            metrics = evaluate(group, scores[offset:end])
            gold = row.get('goldLower', 0)
            observed = next(c for c in row['candidates'] if c.get('label') == 1)
            categories = {
                'target_item': str(row.get('targetItemId', 'UNKNOWN')),
                'observed_candidate_source': observed.get('candidateSource', 'TARGET_PATH'),
                'target_kind': row.get('targetKind', 'UNKNOWN'),
                'completed_core_count': str(min(row.get('completedCoreCount', 0), 3)),
                'position': row.get('position') or 'UNKNOWN',
                'gold_lower_range': '0-499' if gold < 500 else '500-999' if gold < 1000 else '1000-1999' if gold < 2000 else '2000+',
            }
            for category, value in categories.items():
                bucket = buckets.setdefault(category, {}).setdefault(value, {'groups': 0, **{k: 0.0 for k in metrics}})
                bucket['groups'] += 1
                for key, metric in metrics.items():
                    bucket[key] += metric
            offset = end
    for category in buckets.values():
        for bucket in category.values():
            for key in bucket:
                if key != 'groups':
                    bucket[key] /= bucket['groups']
    return buckets
