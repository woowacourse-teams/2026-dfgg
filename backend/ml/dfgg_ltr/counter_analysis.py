"""counter-only 후보가 최종 순위에서 어떻게 되는지 본다.

이번 작업이 고치려던 결함이 여기다. 구조를 "적 + 팀의 아무 아이템"에서 "내 챔피언 + 적 +
아이템"으로 바꿨으므로, counter가 단독으로 찾은 후보가 상위를 차지하면서 정답률은 낮다면
그 경로가 자리만 낭비하고 있다는 뜻이다. **AD/AP hard filter는 두지 않기로** 했으므로
off-meta를 막는 게 아니라, LTR이 균형을 학습했는지를 관측치로 확인한다.
"""
from __future__ import annotations

import math
from collections import defaultdict
from typing import Sequence

import numpy as np

#: generator가 이 후보를 찾았는지 표시하는 플래그. FeatureName 선언 순서와 같다.
SOURCE_FEATURES = ["source_build", "source_self_synergy", "source_ally_synergy", "source_counter"]
_SOURCE_LABELS = ["BUILD", "SELF_SYNERGY", "ALLY_SYNERGY", "COUNTER"]

GROUND_TRUTH_GRADE = 3


def source_combination(row: Sequence[float]) -> str:
    """이 후보를 찾은 generator들의 이름. 선언 순서로 고정한다."""
    found = [
        label for index, label in enumerate(_SOURCE_LABELS)
        if not (row[index] is None or math.isnan(row[index])) and row[index] > 0
    ]
    return "+".join(found) if found else "NONE"


def analyze_sources(
        features: np.ndarray,
        scores: np.ndarray,
        group: Sequence[int],
        top_k: int,
        labels: np.ndarray | None = None,
) -> dict[str, dict]:
    """source 조합별로 후보 수·top-k 점유율·평균 최종 순위·정답률을 낸다."""
    total = int(np.sum(group))
    if total != len(features):
        raise ValueError(f"group의 합이 행 수와 다릅니다: group={total}, rows={len(features)}")

    counts: dict[str, int] = defaultdict(int)
    in_top_k: dict[str, int] = defaultdict(int)
    rank_sum: dict[str, int] = defaultdict(int)
    ground_truth: dict[str, int] = defaultdict(int)

    offset = 0
    for size in group:
        block = slice(offset, offset + size)
        order = np.argsort(-scores[block], kind="stable")
        offset += size

        for rank, position in enumerate(order, start=1):
            row = features[block][position]
            combination = source_combination(row)
            counts[combination] += 1
            rank_sum[combination] += rank
            if rank <= top_k:
                in_top_k[combination] += 1
            if labels is not None and labels[block][position] == GROUND_TRUTH_GRADE:
                ground_truth[combination] += 1

    result: dict[str, dict] = {}
    for combination, count in counts.items():
        entry = {
            "candidates": count,
            "top_k_rate": in_top_k[combination] / count,
            "mean_rank": rank_sum[combination] / count,
        }
        if labels is not None:
            entry["ground_truth_rate"] = ground_truth[combination] / count
        result[combination] = entry
    return result


AP_TAG = "SpellDamage"
AD_TAG = "Damage"


def item_type(tags: Sequence[str]) -> str:
    """Data Dragon 태그로 AD/AP를 가른다. 둘 다 또는 어느 쪽도 아니면 OTHER."""
    has_ap = AP_TAG in tags
    has_ad = AD_TAG in tags
    if has_ap and not has_ad:
        return "AP"
    if has_ad and not has_ap:
        return "AD"
    return "OTHER"


def champion_ap_share(
        champion_ids: Sequence[int],
        item_ids: Sequence[int],
        labels: np.ndarray,
        item_types: dict[int, str],
) -> dict[int, float]:
    """챔피언별로 **실제 구매한** 아이템 중 AP 비중.

    태그로 "야스오는 AD 챔피언"이라고 단정하면 논쟁이 되지만, 실제 코어 구매 중 AP가 몇 %인지는
    데이터가 답한다. AD/AP 어느 쪽도 아닌 아이템은 분모에서 뺀다 — 성향을 말하지 않는 값이다.
    타입이 있는 구매가 하나도 없는 챔피언은 결과에 넣지 않는다. 0으로 두면
    "AP를 전혀 안 사는 챔피언"과 구분되지 않는다.
    """
    ap_counts: dict[int, int] = defaultdict(int)
    typed_counts: dict[int, int] = defaultdict(int)

    for champion_id, item_id, label in zip(champion_ids, item_ids, labels):
        if label != GROUND_TRUTH_GRADE:
            continue
        kind = item_types.get(item_id, "OTHER")
        if kind == "OTHER":
            continue
        typed_counts[champion_id] += 1
        if kind == "AP":
            ap_counts[champion_id] += 1

    return {
        champion_id: ap_counts[champion_id] / count
        for champion_id, count in typed_counts.items()
    }


#: 이 밖으로 벗어나야 "성향이 뚜렷한 챔피언"으로 본다. 중간대는 어느 쪽도 어긋난 추천이 아니다.
AD_CHAMPION_MAX_AP_SHARE = 0.15
AP_CHAMPION_MIN_AP_SHARE = 0.85


def off_type_top_k(
        features: np.ndarray,
        scores: np.ndarray,
        group: Sequence[int],
        champion_ids: Sequence[int],
        item_ids: Sequence[int],
        item_types: dict[int, str],
        champion_ap_shares: dict[int, float],
        top_k: int,
) -> dict[str, int]:
    """상위 K칸 중 챔피언 성향과 어긋난 아이템이 몇 칸인지, 그중 counter 단독이 몇 칸인지.

    이번 작업이 고치려던 결함의 최종 지표다. 구 구조는 "적 + 팀의 아무 아이템"이라 야스오에게
    존야가 올라올 수 있었다. **AD/AP hard filter는 두지 않기로** 했으므로 0%가 목표가 아니다 —
    off-meta 빌드는 남아야 한다. counter 단독 경로의 비중이 결함의 잔량을 말해준다.
    """
    typed_slots = 0
    off_type_slots = 0
    off_type_from_counter_only = 0

    offset = 0
    for size in group:
        block = slice(offset, offset + size)
        order = np.argsort(-scores[block], kind="stable")[:top_k]
        block_start = offset
        offset += size

        for position in order:
            index = block_start + position
            share = champion_ap_shares.get(champion_ids[index])
            kind = item_types.get(item_ids[index], "OTHER")
            if share is None or kind == "OTHER":
                continue
            prefers_ad = share <= AD_CHAMPION_MAX_AP_SHARE
            prefers_ap = share >= AP_CHAMPION_MIN_AP_SHARE
            if not (prefers_ad or prefers_ap):
                continue

            typed_slots += 1
            if (prefers_ad and kind == "AP") or (prefers_ap and kind == "AD"):
                off_type_slots += 1
                if source_combination(features[index]) == "COUNTER":
                    off_type_from_counter_only += 1

    return {
        "typed_slots": typed_slots,
        "off_type_slots": off_type_slots,
        "off_type_from_counter_only": off_type_from_counter_only,
    }
