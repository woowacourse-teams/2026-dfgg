"""평탄화한 트리를 순회하는 참조 구현.

Java의 `GradientBoostedTrees`가 구현해야 할 규약을 Python으로 그대로 적은 것이다.
이 구현이 LightGBM 원본과 일치함은 `tests/test_flatten_equivalence.py`가 보장하고,
Java가 이 구현과 일치함은 Java쪽 parity 테스트가 보장한다. 두 고리가 이어져
"LightGBM == Java"가 성립한다. 그래서 이 함수는 테스트가 아니라 모듈에 둔다.
"""
from __future__ import annotations

import math
from typing import Sequence


def predict_with_flat_trees(trees: list[dict], row: Sequence[float]) -> float:
    """자식 인덱스가 0 이상이면 분기 노드, 음수면 잎(`-index - 1`)이다."""
    total = 0.0
    for tree in trees:
        if not tree["split_feature"]:
            total += tree["leaf_value"][0]
            continue
        node = 0
        while node >= 0:
            value = row[tree["split_feature"][node]]
            if value is None or math.isnan(value):
                go_left = tree["default_left"][node]
            else:
                go_left = value <= tree["threshold"][node]
            node = tree["left"][node] if go_left else tree["right"][node]
        total += tree["leaf_value"][-node - 1]
    return total
