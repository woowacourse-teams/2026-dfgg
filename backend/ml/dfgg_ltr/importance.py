"""모델이 어느 묶음에 실제로 기대는지 잰다.

두 방식을 같이 본다. SHAP은 "예측을 얼마나 밀었나"(크기)를, permutation은 "그 값이 없으면
지표가 얼마나 나빠지나"(쓸모)를 말한다. counter처럼 값은 크지만 예측력은 없는 경우 둘이
갈리므로, 하나만 보면 오독한다.
"""
from __future__ import annotations

from typing import Sequence

import lightgbm as lgb
import numpy as np

from dfgg_ltr.metrics import evaluate_ranking


def _validate(groups: dict[str, Sequence[int]], feature_count: int) -> None:
    for name, indices in groups.items():
        for index in indices:
            if index >= feature_count:
                raise ValueError(
                    f"그룹 {name}이 모델에 없는 열을 가리킵니다: {index} (feature {feature_count}개)")


def shap_importance_by_group(
        booster: lgb.Booster, features: np.ndarray, groups: dict[str, Sequence[int]]
) -> dict[str, float]:
    """묶음별 평균 |SHAP 기여도|. 부호가 섞여 상쇄되지 않도록 절댓값을 먼저 취한다."""
    _validate(groups, features.shape[1])
    # 마지막 열은 base value라 뺀다.
    contributions = np.abs(booster.predict(features, pred_contrib=True)[:, :-1])
    return {
        name: float(contributions[:, list(indices)].sum(axis=1).mean())
        for name, indices in groups.items()
    }


def permutation_importance_by_group(
        booster: lgb.Booster,
        features: np.ndarray,
        labels: np.ndarray,
        group: Sequence[int],
        groups: dict[str, Sequence[int]],
        repeats: int = 3,
        seed: int = 0,
        metric: str = "ndcg@5",
) -> dict[str, float]:
    """묶음의 열들을 함께 섞었을 때 지표가 떨어지는 폭.

    한 열씩 섞으면 같은 정보를 담은 이웃 열이 빈자리를 메워 중요도가 과소평가된다.
    묶음 단위로 섞어야 "이 근거가 통째로 없으면"에 답할 수 있다.
    """
    _validate(groups, features.shape[1])

    baseline = evaluate_ranking(
        labels, booster.predict(features, raw_score=True), list(group))[metric]

    importance: dict[str, float] = {}
    for name, indices in groups.items():
        drops = []
        for repeat in range(repeats):
            rng = np.random.default_rng(seed + repeat)
            # 호출자의 행렬을 건드리면 이후 분석이 전부 오염된다. 복사본에서 섞는다.
            shuffled = features.copy()
            permutation = rng.permutation(len(shuffled))
            shuffled[:, list(indices)] = shuffled[permutation][:, list(indices)]

            score = evaluate_ranking(
                labels, booster.predict(shuffled, raw_score=True), list(group))[metric]
            drops.append(baseline - score)
        importance[name] = float(np.mean(drops))
    return importance
