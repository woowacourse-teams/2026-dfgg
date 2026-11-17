"""게임 상황 값이 순위를 상황에 맞게 바꾸는지 본다.

상황 값(상대 주문력 비중, 맞라인 레벨 차 등)은 한 요청의 모든 후보가 같은 값을 갖는다. 그래서
`scenario.py`처럼 가짜 후보를 만들어 묻지 않고, 실제 후보 묶음에서 그 값만 바꿔 다시 점수를 매긴다.

feature는 여기서 계산하지 않는다. Java가 내보낸 벡터의 열을 그대로 읽고, 아이템은 Java가 내보낸
아이템 정보의 태그로 고른다.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Callable, Collection, Sequence

import numpy as np

from dfgg_ltr.compare import top_items
from dfgg_ltr.dataset import Dataset
from dfgg_ltr.metrics import evaluate_ranking


def load_item_catalog(path: Path) -> dict[int, dict]:
    """아이템 번호 → {name, tags, total_gold}. Java의 `ItemCatalogExporter`가 쓴 파일이다."""
    catalog = json.loads(Path(path).read_text(encoding="utf-8"))
    return {int(item["item_id"]): item for item in catalog["items"]}


def items_with_tag(catalog: dict[int, dict], tag: str) -> set[int]:
    return {item_id for item_id, item in catalog.items() if tag in item["tags"]}


def _offsets(group: Sequence[int]) -> np.ndarray:
    """query마다 첫 행의 위치."""
    return np.concatenate(([0], np.cumsum(group)[:-1])).astype(int)


def query_values(features: np.ndarray, group: Sequence[int], column: int) -> np.ndarray:
    """query 수준 feature의 값을 query마다 하나씩. 모든 후보가 같은 값이라 첫 행에서 읽는다."""
    return features[_offsets(group), column]


def substitute(features: np.ndarray, group: Sequence[int], column: int, value: float,
               queries: np.ndarray) -> np.ndarray:
    """고른 query의 모든 후보에 같은 값을 넣은 복사본. 실제 서빙에서 이 값은 요청마다 하나다."""
    rows = np.repeat(np.asarray(queries, dtype=bool), group)
    # 호출자의 행렬을 건드리면 이후 분석이 전부 오염된다.
    changed = features.copy()
    changed[rows, column] = value
    return changed


def best_rank_of(scores: np.ndarray, group: Sequence[int], item_ids: np.ndarray,
                 targets: Collection[int]) -> np.ndarray:
    """query마다 대상 아이템 중 가장 높은 순위(1부터). 대상이 후보에 없으면 NaN — 꼴찌로 세지 않는다."""
    ranks = []
    for ordered in top_items(scores, group, item_ids, k=max(group, default=0)):
        positions = [rank for rank, item in enumerate(ordered, start=1) if item in targets]
        ranks.append(float(positions[0]) if positions else np.nan)
    return np.asarray(ranks, dtype=float)


def top1_share(scores: np.ndarray, group: Sequence[int], item_ids: np.ndarray,
               targets: Collection[int], queries: np.ndarray) -> float:
    """고른 query 중 1순위가 대상 아이템인 비율."""
    firsts = top_items(scores, group, item_ids, k=1)
    if len(queries) != len(firsts):
        raise ValueError(f"query 표시의 길이가 query 수와 다릅니다: {len(queries)} != {len(firsts)}")
    selected = [first for first, chosen in zip(firsts, queries) if chosen]
    if not selected:
        return float("nan")
    return sum(1 for first in selected if first and first[0] in targets) / len(selected)


def low_and_high_values(dataset: Dataset, column: int, queries: np.ndarray,
                        low_percentile: float = 10, high_percentile: float = 90) -> tuple[float, float]:
    """고른 query에서 실제로 관측된 값의 하위·상위 분위수. 값을 바꿔 넣을 때 "낮음"과 "높음"으로 쓴다."""
    observed = query_values(dataset.features, dataset.group, column)[np.asarray(queries, dtype=bool)]
    return (float(np.nanpercentile(observed, low_percentile)),
            float(np.nanpercentile(observed, high_percentile)))


def substitution_effect(predict: Callable[[np.ndarray], np.ndarray], dataset: Dataset, column: int,
                        targets: Collection[int], queries: np.ndarray,
                        low_percentile: float = 10, high_percentile: float = 90) -> dict[str, float | int]:
    """상황 값 하나를 낮음(하위 10%)과 높음(상위 10%)으로 바꿔 넣었을 때 대상 아이템의 순위를 비교한다.

    실제로는 없는 조합을 만들 수 있다 — 방향을 확인하는 용도이고, 실제 판을 나눠 본 결과와 같이 읽는다.

    :param queries: 값을 바꿔 넣을 query. 상황을 아는 query만 고른다
    """
    queries = np.asarray(queries, dtype=bool)
    low_value, high_value = low_and_high_values(dataset, column, queries, low_percentile, high_percentile)

    low_scores = predict(substitute(dataset.features, dataset.group, column, low_value, queries))
    high_scores = predict(substitute(dataset.features, dataset.group, column, high_value, queries))
    low_ranks = best_rank_of(low_scores, dataset.group, dataset.item_ids, targets)
    high_ranks = best_rank_of(high_scores, dataset.group, dataset.item_ids, targets)

    # 대상 아이템이 후보에 있는 query만 센다.
    counted = queries & ~np.isnan(low_ranks)
    return {
        "low_value": low_value,
        "high_value": high_value,
        "queries": int(counted.sum()),
        "mean_rank_low": float(low_ranks[counted].mean()) if counted.any() else float("nan"),
        "mean_rank_high": float(high_ranks[counted].mean()) if counted.any() else float("nan"),
        "top1_share_low": float((low_ranks[counted] == 1).mean()) if counted.any() else float("nan"),
        "top1_share_high": float((high_ranks[counted] == 1).mean()) if counted.any() else float("nan"),
        "top3_share_low": float((low_ranks[counted] <= 3).mean()) if counted.any() else float("nan"),
        "top3_share_high": float((high_ranks[counted] <= 3).mean()) if counted.any() else float("nan"),
    }


def empty_value_rates(dataset: Dataset, columns: Sequence[int], steps: Sequence[int],
                      queries: np.ndarray | None = None) -> list[dict]:
    """구매 단계별로, 값이 비어 있거나(NaN) 0인 query의 비율.

    첫 번째·두 번째 Core를 고를 때는 아직 아무도 Core를 사지 않아 아이템 값이 비기 쉽다.
    Core만 봐서 놓치는 양을 재서, 조합(재료) 아이템까지 볼지를 정하는 데 쓴다.

    :param queries: 셀 query. 상황을 아는 query만 고른다 — 상황 자체를 몰라 빈 것과 섞이지 않게 한다
    """
    rates = []
    for step in steps:
        at_step = dataset.purchase_steps == step
        if queries is not None:
            at_step = at_step & np.asarray(queries, dtype=bool)
        for column in columns:
            values = query_values(dataset.features, dataset.group, column)[at_step]
            rates.append({
                "purchase_step": int(step),
                "feature": dataset.feature_names[column],
                "queries": int(at_step.sum()),
                "missing": float(np.isnan(values).mean()) if len(values) else float("nan"),
                "zero": float((values == 0).mean()) if len(values) else float("nan"),
            })
    return rates


def query_permutation_importance(predict: Callable[[np.ndarray], np.ndarray], dataset: Dataset,
                                 groups: dict[str, Sequence[int]], repeats: int = 3, seed: int = 0,
                                 metric: str = "ndcg@5") -> dict[str, float]:
    """query 수준 feature 묶음을 **query 단위로** 섞었을 때 지표가 떨어지는 폭.

    상황 값은 한 요청의 모든 후보가 같은 값을 갖는다. `importance.permutation_importance_by_group`처럼
    행 단위로 섞으면 같은 요청의 후보마다 다른 값이 들어가, "그 값이 없으면"이 아니라 "후보마다 다른 상황을
    넣으면"을 재게 된다 — 순위에 아무 영향이 없는 값에도 중요도가 생긴다. 그래서 요청의 값을 통째로 다른
    요청의 값과 바꾼다. 한 묶음의 열들은 같은 요청에서 함께 가져와 서로의 관계를 깨지 않는다.
    """
    group = list(dataset.group)
    baseline = evaluate_ranking(dataset.labels, predict(dataset.features), group)[metric]

    importance: dict[str, float] = {}
    for name, columns in groups.items():
        drops = []
        for repeat in range(repeats):
            rng = np.random.default_rng(seed + repeat)
            order = rng.permutation(len(group))
            # 호출자의 행렬을 건드리면 이후 분석이 전부 오염된다. 복사본에서 섞는다.
            shuffled = dataset.features.copy()
            for column in columns:
                values = query_values(dataset.features, group, column)
                shuffled[:, column] = np.repeat(values[order], group)
            score = evaluate_ranking(dataset.labels, predict(shuffled), group)[metric]
            drops.append(baseline - score)
        importance[name] = float(np.mean(drops))
    return importance
