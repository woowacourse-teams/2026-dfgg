"""LightGBM 모델을 Java 추론이 읽는 평탄 형식으로 변환한다.

`dump_model()`은 `left_child`/`right_child`가 중첩된 dict를 준다. Java에서 중첩 구조를 그대로
읽으면 재귀 파싱이 필요하고 노드 접근이 흩어지는데, 평탄 배열이면 인덱스 순회만으로 끝난다.

**규약**: 자식 인덱스가 0 이상이면 분기 노드 인덱스, 음수면 잎이며 `-index - 1`이 잎 번호다.
LightGBM 내부 표현과 같은 방식이라 변환이 단순하고, Java 쪽에서도 분기 하나로 판별된다.
"""
from __future__ import annotations

from typing import Any

import lightgbm as lgb

NUMERIC_DECISION_TYPES = {"<=", "<"}


def flatten_model(dumped: dict[str, Any]) -> list[dict[str, Any]]:
    return [_flatten_tree(tree["tree_structure"]) for tree in dumped["tree_info"]]


def _flatten_tree(root: dict[str, Any]) -> dict[str, Any]:
    tree: dict[str, list[Any]] = {
        "split_feature": [], "threshold": [], "default_left": [],
        "left": [], "right": [], "leaf_value": [],
        # TreeSHAP은 각 노드를 지난 학습 표본 수가 있어야 계산된다. 분기 조건과 무관한
        # feature의 기여도를 "양쪽으로 얼마나 갈렸는가"의 비율로 나누기 때문이다.
        "node_cover": [], "leaf_cover": [],
    }
    _visit(root, tree)
    return tree


def _visit(node: dict[str, Any], tree: dict[str, list[Any]]) -> int:
    """노드를 배열에 넣고 자식 인덱스 규약에 맞는 값을 돌려준다."""
    if "leaf_value" in node:
        tree["leaf_value"].append(float(node["leaf_value"]))
        # 트리가 잎 하나뿐이면 leaf_count가 없다. 그때 cover는 학습 표본 전체다.
        tree["leaf_cover"].append(float(node.get("leaf_count", node.get("internal_count", 1))))
        return -len(tree["leaf_value"])  # 잎 0번 → -1, 1번 → -2 ...

    decision_type = node.get("decision_type", "<=")
    if decision_type not in NUMERIC_DECISION_TYPES:
        raise ValueError(
            f"범주형(categorical) 분기는 지원하지 않습니다: decision_type={decision_type}. "
            "Java 추론이 numeric split만 다루기로 했으므로 범주형 feature 없이 학습해야 합니다."
        )

    index = len(tree["split_feature"])
    tree["split_feature"].append(int(node["split_feature"]))
    tree["threshold"].append(float(node["threshold"]))
    tree["default_left"].append(bool(node.get("default_left", True)))
    tree["node_cover"].append(float(node["internal_count"]))
    tree["left"].append(0)   # 자리만 잡고 자식 방문 후 채운다
    tree["right"].append(0)

    tree["left"][index] = _visit(node["left_child"], tree)
    tree["right"][index] = _visit(node["right_child"], tree)
    return index


def to_export_dict(
    booster: lgb.Booster, feature_names: list[str], schema_fingerprint: str
) -> dict[str, Any]:
    dumped = booster.dump_model()
    if len(dumped["feature_names"]) != len(feature_names):
        raise ValueError(
            f"모델의 feature 수와 스키마 이름 수가 다릅니다: "
            f"{len(dumped['feature_names'])} != {len(feature_names)}"
        )
    return {
        "schema_fingerprint": schema_fingerprint,
        "feature_names": feature_names,
        "objective": dumped["objective"],
        "trees": flatten_model(dumped),
    }
