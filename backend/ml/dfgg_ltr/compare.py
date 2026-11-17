"""두 모델이 같은 스냅샷에서 내는 순위를 비교한다.

지표가 같아도 추천은 다를 수 있다. "1순위가 바뀐 비율"과 "상위 3개 구성이 바뀐 비율"을 따로 잰다.
씨앗만 다른 두 모델에도 같은 계산을 하면 그 값이 잡음 바닥이다 — 그보다 큰 변화만 feature 덕으로 읽는다.
"""
from __future__ import annotations

from typing import Sequence

import numpy as np


def top_items(scores: np.ndarray, group: Sequence[int], item_ids: np.ndarray, k: int) -> list[tuple[int, ...]]:
    """query마다 상위 k개의 아이템 번호. 점수가 같으면 번호가 작은 쪽이 앞이다(Java 랭커의 동점 규칙)."""
    if int(np.sum(group)) != len(scores) or len(scores) != len(item_ids):
        raise ValueError(
            f"행 수가 맞지 않습니다: group 합={int(np.sum(group))}, scores={len(scores)}, item_ids={len(item_ids)}")
    tops: list[tuple[int, ...]] = []
    offset = 0
    for size in group:
        query_scores = scores[offset:offset + size]
        query_items = item_ids[offset:offset + size]
        offset += size
        # lexsort는 마지막 키가 1순위다. 점수 내림차순, 같으면 아이템 번호 오름차순.
        order = np.lexsort((query_items, -query_scores))
        tops.append(tuple(int(item) for item in query_items[order][:k]))
    return tops


def rank_change(first_scores: np.ndarray, second_scores: np.ndarray,
                group: Sequence[int], item_ids: np.ndarray,
                queries: np.ndarray | None = None) -> dict[str, float | int]:
    """같은 후보에 두 모델이 매긴 점수로, 1순위가 다른 query와 상위 3개 구성이 다른 query의 비율을 낸다.

    :param queries: 잴 query를 고르는 표시. 주지 않으면 전부 잰다
    """
    first = top_items(first_scores, group, item_ids, k=3)
    second = top_items(second_scores, group, item_ids, k=3)
    if queries is not None:
        if len(queries) != len(first):
            raise ValueError(f"query 표시의 길이가 query 수와 다릅니다: {len(queries)} != {len(first)}")
        first = [tops for tops, chosen in zip(first, queries) if chosen]
        second = [tops for tops, chosen in zip(second, queries) if chosen]
    queries = len(first)
    top1_changed = sum(1 for a, b in zip(first, second) if a[:1] != b[:1])
    top3_changed = sum(1 for a, b in zip(first, second) if set(a) != set(b))
    return {
        "top1_changed": top1_changed / queries if queries else float("nan"),
        "top3_changed": top3_changed / queries if queries else float("nan"),
        "queries": queries,
    }
