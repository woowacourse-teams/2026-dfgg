"""랭킹 지표.

NDCG만으로는 "정답을 몇 위에 뒀는가"가 보이지 않아 MRR·HitRate를 함께 본다.
등급은 `RelevanceLabeler`가 매긴 것이다: 3 = 실제 다음 구매(정답), 2 = 같은 게임의 나중 구매,
1 = 기본 구매율이 충분히 높은 아이템, 0 = 나머지. **정답은 등급 3 하나뿐이다.**
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

GROUND_TRUTH_GRADE = 3
P_MIN = 0.02
BOOTSTRAP_ROUNDS = 1000
BOOTSTRAP_CHUNK = 100


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


# --- 승률 지표 V: 모델 1순위를 따랐을 때의 승률 (self-normalized IPS) ---
#   V   = Σ [1순위 = 실제 구매] · r / p  ÷  Σ [1순위 = 실제 구매] / p
#   ESS = (Σ w)² / Σ w²


@dataclass(frozen=True)
class PolicyValue:
    """V와 그 신뢰도. 1순위가 실제 구매와 같은 query가 없으면 V와 구간은 NaN이다.

    coverage는 p와 승패를 모두 아는 query의 비율이고, observed_win은 그 query들의 관측 승률(비교 기준선)이다.
    """

    value: float
    lower: float
    upper: float
    ess: float
    matched: int
    usable: int
    coverage: float
    clipped_share: float
    observed_win: float


def top1_matches(labels: np.ndarray, scores: np.ndarray, group: np.ndarray) -> np.ndarray:
    """query마다 1순위가 실제로 산 아이템(등급 3)인지."""
    bounds = np.concatenate([[0], np.cumsum(group)])
    matches = np.zeros(len(group), dtype=bool)
    for index, (start, end) in enumerate(zip(bounds[:-1], bounds[1:])):
        top = start + int(np.argsort(-scores[start:end], kind="stable")[0])
        matches[index] = labels[top] == GROUND_TRUTH_GRADE
    return matches


def policy_value(matches: np.ndarray, propensity: np.ndarray, win: np.ndarray, match_ids: list[str],
                 p_min: float = P_MIN, rounds: int = BOOTSTRAP_ROUNDS, seed: int = 0) -> PolicyValue:
    """p나 승패를 모르는 query는 0으로 세지 않고 건너뛴다. 구간은 경기 단위 부트스트랩이다(같은 경기의 query는 승패를 공유)."""
    usable = _usable(propensity, win)
    weights = _weights(matches, propensity, usable, p_min)
    rewards = np.nan_to_num(win)
    lower, upper = _bootstrap_interval([weights], rewards, match_ids, usable, rounds, seed)
    matched = matches & usable
    return PolicyValue(
        value=_ratio((weights * rewards).sum(), weights.sum()),
        lower=lower,
        upper=upper,
        ess=_effective_sample_size(weights),
        matched=int(matched.sum()),
        usable=int(usable.sum()),
        coverage=float(usable.mean()),
        clipped_share=_ratio(float((matched & (propensity < p_min)).sum()), float(matched.sum())),
        observed_win=_ratio(rewards[usable].sum(), float(usable.sum())),
    )


def policy_value_difference(candidate_matches: np.ndarray, baseline_matches: np.ndarray, propensity: np.ndarray,
                            win: np.ndarray, match_ids: list[str], p_min: float = P_MIN,
                            rounds: int = BOOTSTRAP_ROUNDS, seed: int = 0) -> dict[str, float]:
    """후보 V − 기준 V와 95% 구간. 같은 부트스트랩 표본으로 짝지어 재서 두 모델이 공유하는 흔들림을 지운다."""
    usable = _usable(propensity, win)
    rewards = np.nan_to_num(win)
    candidate = _weights(candidate_matches, propensity, usable, p_min)
    baseline = _weights(baseline_matches, propensity, usable, p_min)
    difference = (_ratio((candidate * rewards).sum(), candidate.sum())
                  - _ratio((baseline * rewards).sum(), baseline.sum()))
    lower, upper = _bootstrap_interval([candidate, baseline], rewards, match_ids, usable, rounds, seed,
                                       combine=lambda values: values[0] - values[1])
    return {"difference": difference, "lower": lower, "upper": upper}


def hit_rate_by_outcome(labels: np.ndarray, scores: np.ndarray, group: np.ndarray, win: np.ndarray,
                        k: int = 5) -> dict[str, float]:
    """이긴 게임과 진 게임의 HitRate@k, 그 차이(win lift = 승리 − 패배). 승패를 모르는 query는 뺀다."""
    bounds = np.concatenate([[0], np.cumsum(group)])
    hits = np.array([_nan_if_none(hit_rate_at_k(labels[start:end], scores[start:end], k))
                     for start, end in zip(bounds[:-1], bounds[1:])])
    scored = ~np.isnan(hits)
    won = scored & (win == 1)
    lost = scored & (win == 0)
    win_rate = _ratio(hits[won].sum(), float(won.sum()))
    loss_rate = _ratio(hits[lost].sum(), float(lost.sum()))
    return {f"hit_rate@{k}_win": win_rate, f"hit_rate@{k}_loss": loss_rate, "win_lift": win_rate - loss_rate}


def _nan_if_none(value: float | None) -> float:
    if value is None:
        return float("nan")
    return value


def _usable(propensity: np.ndarray, win: np.ndarray) -> np.ndarray:
    return ~np.isnan(propensity) & ~np.isnan(win)


def _weights(matches: np.ndarray, propensity: np.ndarray, usable: np.ndarray, p_min: float) -> np.ndarray:
    """1순위가 실제 구매와 같고 쓸 수 있는 query만 1 / max(p, 하한), 나머지는 0."""
    clipped = np.clip(np.nan_to_num(propensity, nan=1.0), p_min, None)
    return np.where(matches & usable, 1.0 / clipped, 0.0)


def _effective_sample_size(weights: np.ndarray) -> float:
    squared = float((weights ** 2).sum())
    if squared == 0.0:
        return 0.0
    return float(weights.sum() ** 2 / squared)


def _ratio(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return float("nan")
    return float(numerator / denominator)


def _bootstrap_interval(weight_sets: list[np.ndarray], rewards: np.ndarray, match_ids: list[str],
                        usable: np.ndarray, rounds: int, seed: int,
                        combine=lambda values: values[0]) -> tuple[float, float]:
    """쓸 수 있는 query가 속한 경기를 복원 추출해 V(또는 V 차이)의 2.5%·97.5% 분위를 낸다."""
    ids = np.asarray(match_ids)[usable]
    if ids.size == 0:
        return float("nan"), float("nan")
    _, cluster = np.unique(ids, return_inverse=True)
    clusters = cluster.max() + 1
    sums = [(np.bincount(cluster, (w * rewards)[usable], clusters), np.bincount(cluster, w[usable], clusters))
            for w in weight_sets]
    rng = np.random.default_rng(seed)
    samples = []
    for start in range(0, rounds, BOOTSTRAP_CHUNK):
        draws = rng.integers(0, clusters, (min(BOOTSTRAP_CHUNK, rounds - start), clusters))
        values = []
        for weighted_rewards, weight_totals in sums:
            numerators = weighted_rewards[draws].sum(axis=1)
            denominators = weight_totals[draws].sum(axis=1)
            with np.errstate(invalid="ignore", divide="ignore"):
                values.append(numerators / denominators)
        samples.append(combine(values))
    samples = np.concatenate(samples)
    samples = samples[~np.isnan(samples)]
    if samples.size == 0:
        return float("nan"), float("nan")
    lower, upper = np.percentile(samples, [2.5, 97.5])
    return float(lower), float(upper)


# --- DR 승률: 승리 확률 모델 Q의 직접 추정에 IPS 보정을 더한다 ---
#   DR = 평균 [ Q(x, π(x)) + [π(x) = 실제 구매] × (r − Q(x, π(x))) / p ]
#   1순위가 실제 구매와 다른 query도 Q로 값을 내므로 SNIPS V보다 분산이 작다.


def top1_values(values: np.ndarray, scores: np.ndarray, group: np.ndarray) -> np.ndarray:
    """query마다 점수 1순위 행의 값."""
    bounds = np.concatenate([[0], np.cumsum(group)])
    result = np.empty(len(group))
    for index, (start, end) in enumerate(zip(bounds[:-1], bounds[1:])):
        result[index] = values[start + int(np.argsort(-scores[start:end], kind="stable")[0])]
    return result


def doubly_robust_terms(matches: np.ndarray, q_top: np.ndarray, propensity: np.ndarray, win: np.ndarray,
                        p_min: float = P_MIN) -> np.ndarray:
    """query마다 DR 항. p나 승패를 모르는 query는 NaN이다."""
    usable = _usable(propensity, win) & ~np.isnan(q_top)
    weights = _weights(matches, propensity, usable, p_min)
    return np.where(usable, q_top + weights * (np.nan_to_num(win) - q_top), np.nan)


def doubly_robust_value(terms: np.ndarray, match_ids: list[str], rounds: int = BOOTSTRAP_ROUNDS,
                        seed: int = 0) -> dict[str, float]:
    """DR 승률과 경기 단위 부트스트랩 95% 구간."""
    lower, upper = _cluster_mean_interval(terms, match_ids, rounds, seed)
    return {"value": _nan_mean(terms), "lower": lower, "upper": upper, "usable": int((~np.isnan(terms)).sum())}


def doubly_robust_difference(candidate_terms: np.ndarray, baseline_terms: np.ndarray, match_ids: list[str],
                             rounds: int = BOOTSTRAP_ROUNDS, seed: int = 0) -> dict[str, float]:
    """후보 DR − 기준 DR과 95% 구간. query마다 짝지은 차이를 부트스트랩한다."""
    difference = candidate_terms - baseline_terms
    lower, upper = _cluster_mean_interval(difference, match_ids, rounds, seed)
    return {"difference": _nan_mean(difference), "lower": lower, "upper": upper}


def _nan_mean(values: np.ndarray) -> float:
    known = values[~np.isnan(values)]
    if known.size == 0:
        return float("nan")
    return float(known.mean())


def _cluster_mean_interval(values: np.ndarray, match_ids: list[str], rounds: int,
                           seed: int) -> tuple[float, float]:
    """값을 아는 query가 속한 경기를 복원 추출해 평균의 2.5%, 97.5% 분위를 낸다."""
    known = ~np.isnan(values)
    ids = np.asarray(match_ids)[known]
    if ids.size == 0:
        return float("nan"), float("nan")
    _, cluster = np.unique(ids, return_inverse=True)
    clusters = cluster.max() + 1
    sums = np.bincount(cluster, values[known], clusters)
    counts = np.bincount(cluster, minlength=clusters).astype(float)
    rng = np.random.default_rng(seed)
    samples = []
    for start in range(0, rounds, BOOTSTRAP_CHUNK):
        draws = rng.integers(0, clusters, (min(BOOTSTRAP_CHUNK, rounds - start), clusters))
        samples.append(sums[draws].sum(axis=1) / counts[draws].sum(axis=1))
    lower, upper = np.percentile(np.concatenate(samples), [2.5, 97.5])
    return float(lower), float(upper)
