"""feature 그룹을 빼고 학습해 그 그룹의 기여를 잰다.

특히 패치 민감 feature가 실제로 일하는지 확인한다. 이 그룹이 없어도 지표가 같다면
"패치 버프/너프를 반영한다"는 설계가 말뿐이라는 뜻이다.
"""
from __future__ import annotations

import dataclasses
from typing import Sequence

from dfgg_ltr.dataset import Dataset

#: 최근 패치 창과 패치별 메타를 읽는 feature들. 이름으로 고르지 않고 그룹으로 묶어 둔다.
PATCH_AWARE = "patch_aware"

_GROUPS = {
    PATCH_AWARE: lambda name: "recent" in name or "patch" in name,
}


def select_feature_indices(feature_names: Sequence[str], drop) -> list[int]:
    """남길 feature의 인덱스를 선언 순서 그대로 낸다.

    이름이 하나도 걸리지 않으면 예외를 던진다. 오타 때문에 아무것도 빠지지 않은 채
    "차이가 없다"는 결론이 나오는 게 이 실험에서 가장 위험하다.
    """
    if isinstance(drop, str):
        if drop not in _GROUPS:
            raise ValueError(f"알 수 없는 feature 그룹입니다: {drop}")
        matches = _GROUPS[drop]
    else:
        dropped = set(drop)
        unknown = dropped - set(feature_names)
        if unknown:
            raise ValueError(f"스키마에 없는 feature를 빼려고 합니다: {sorted(unknown)}")
        matches = dropped.__contains__

    kept = [index for index, name in enumerate(feature_names) if not matches(name)]
    if not kept:
        raise ValueError("모든 feature를 뺐습니다. 학습할 게 남지 않습니다.")
    if len(kept) == len(feature_names):
        raise ValueError("빠진 feature가 없습니다. 그룹 정의나 이름을 확인하세요.")
    return kept


def apply_feature_mask(dataset: Dataset, keep_indices: Sequence[int]) -> Dataset:
    """열과 이름을 함께 잘라낸다. 라벨·group은 그대로 둔다."""
    return dataclasses.replace(
        dataset,
        features=dataset.features[:, list(keep_indices)],
        feature_names=[dataset.feature_names[index] for index in keep_indices],
        # 축소된 스키마의 모델이 실수로 서빙되면 Java 로더가 지문에서 걸러야 한다.
        schema_fingerprint=f"{dataset.schema_fingerprint}-ablated{len(keep_indices)}",
    )
