"""랭킹 지표.

NDCG만으로는 "정답을 몇 위에 뒀는가"가 보이지 않아 MRR·HitRate를 함께 본다.
등급은 `RelevanceLabeler`가 매긴 것이다: 3 = 실제 다음 구매(정답), 2 = 같은 게임의 나중 구매,
1 = 기본 구매율이 충분히 높은 아이템, 0 = 나머지. **정답은 등급 3 하나뿐이다.**
"""
from __future__ import annotations

import numpy as np

GROUND_TRUTH_GRADE = 3


def _dcg(gains: np.ndarray) -> float:
    discounts = np.log2(np.arange(2, len(gains) + 2))
    return float(np.sum(gains / discounts))


def ndcg_at_k(labels: np.ndarray, scores: np.ndarray, k: int) -> float | None:
    """관련 아이템이 하나도 없으면 None을 낸다.

    이상적 DCG가 0이라 나눌 수 없다. 0점으로 세면 "틀렸다"는 뜻이 되어 평균이 왜곡된다.
    """
    if not np.any(labels > 0):
        return None
    order = np.argsort(-scores, kind="stable")
    gains = (2.0 ** labels[order][:k]) - 1.0
    ideal = (2.0 ** np.sort(labels)[::-1][:k]) - 1.0
    ideal_dcg = _dcg(ideal)
    if ideal_dcg == 0.0:
        return None
    return _dcg(gains) / ideal_dcg


def _ground_truth_rank(labels: np.ndarray, scores: np.ndarray) -> int | None:
    """정답의 1-based 순위. 정답이 없으면 None."""
    if not np.any(labels == GROUND_TRUTH_GRADE):
        return None
    order = np.argsort(-scores, kind="stable")
    ranked = labels[order]
    return int(np.argmax(ranked == GROUND_TRUTH_GRADE)) + 1


def mrr(labels: np.ndarray, scores: np.ndarray) -> float | None:
    rank = _ground_truth_rank(labels, scores)
    return None if rank is None else 1.0 / rank


def hit_rate_at_k(labels: np.ndarray, scores: np.ndarray, k: int) -> float | None:
    rank = _ground_truth_rank(labels, scores)
    return None if rank is None else float(rank <= k)


def evaluate_ranking(
        labels: np.ndarray, scores: np.ndarray, group: list[int],
        ndcg_ks: tuple[int, ...] = (1, 3, 5), hit_k: int = 5,
) -> dict[str, float | int]:
    """query 경계로 잘라 query마다 지표를 내고 평균낸다.

    전체를 한 덩어리로 정렬하면 "어떤 query에서 잘했는가"가 사라져 지표가 의미를 잃는다.
    정답이 없는 query는 0점으로 세지 않고 건너뛴다.
    """
    total = int(np.sum(group))
    if total != len(labels):
        raise ValueError(f"group의 합이 행 수와 다릅니다: group={total}, rows={len(labels)}")

    sums: dict[str, float] = {f"ndcg@{k}": 0.0 for k in ndcg_ks}
    sums["mrr"] = 0.0
    sums[f"hit_rate@{hit_k}"] = 0.0
    counts: dict[str, int] = {name: 0 for name in sums}

    scored_queries = 0
    offset = 0
    for size in group:
        labels_slice = labels[offset:offset + size]
        scores_slice = scores[offset:offset + size]
        offset += size

        values = {f"ndcg@{k}": ndcg_at_k(labels_slice, scores_slice, k) for k in ndcg_ks}
        values["mrr"] = mrr(labels_slice, scores_slice)
        values[f"hit_rate@{hit_k}"] = hit_rate_at_k(labels_slice, scores_slice, hit_k)

        if values["mrr"] is not None:
            scored_queries += 1
        for name, value in values.items():
            if value is not None:
                sums[name] += value
                counts[name] += 1

    result: dict[str, float | int] = {
        name: (sums[name] / counts[name] if counts[name] else float("nan")) for name in sums
    }
    result["queries"] = scored_queries
    return result
