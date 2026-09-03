"""합성 후보로 "이 근거만 강하면 몇 위인가"를 직접 물어본다.

실데이터 관측은 여러 근거가 뒤섞여 있어 인과를 분리하기 어렵다. 다른 값을 전부 중앙값으로
고정하고 한 묶음만 올리거나 내려서, 그 묶음 하나가 순위를 얼마나 움직이는지 본다.
"""
from __future__ import annotations

from typing import Sequence

import numpy as np


def percentile_profile(features: np.ndarray, low: float = 10, high: float = 90) -> dict[str, np.ndarray]:
    """열별 저·중앙·고 값. 결측은 무시하고 계산한다.

    결측을 그대로 두면 백분위가 NaN이 되어 시나리오 전체가 무의미해진다. 다만 값이 하나도
    없는 열은 중앙값을 지어낼 수 없으므로 결측으로 남긴다.
    """
    import warnings
    with warnings.catch_warnings():
        # 전부 결측인 열은 "All-NaN slice" 경고를 내지만, 결측으로 남기는 게 의도한 동작이다.
        warnings.simplefilter("ignore", RuntimeWarning)
        return {
            "low": np.nanpercentile(features, low, axis=0),
            "median": np.nanpercentile(features, 50, axis=0),
            "high": np.nanpercentile(features, high, axis=0),
        }


def build_scenarios(
        profile: dict[str, np.ndarray],
        groups: dict[str, Sequence[int]],
        scenarios: Sequence[tuple[str, Sequence[str], Sequence[str]]],
) -> tuple[np.ndarray, list[str]]:
    """(이름, 올릴 묶음, 내릴 묶음) 목록을 feature 행렬로 만든다."""
    rows = []
    names = []
    for name, boosted, suppressed in scenarios:
        for group in list(boosted) + list(suppressed):
            if group not in groups:
                raise ValueError(f"알 수 없는 묶음입니다: {group}")

        row = profile["median"].copy()
        for group in boosted:
            row[list(groups[group])] = profile["high"][list(groups[group])]
        for group in suppressed:
            row[list(groups[group])] = profile["low"][list(groups[group])]
        rows.append(row)
        names.append(name)
    return np.array(rows), names
