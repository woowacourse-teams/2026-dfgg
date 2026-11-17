"""재학습한 후보 모델을 현재 운영 모델과 같은 평가셋에서 비교해 교체 여부를 정한다 (U10).

평가셋은 game split의 test다. 후보는 game split의 train으로만 학습해야 한다. 그래야 test 경기가 후보 학습에
들어가지 않는다. patch split은 쓰지 않는다. 배포할 후보는 최신 패치까지 학습하므로 patch test가 학습에 섞인다.

판정 (11/17 승인):
- game test 전체: query ≥ 최소 표본(기본 5,000), NDCG@5 ≥ 현재 − 0.005, HitRate@5 ≥ 현재 − 0.01
- game test 중 최신 패치: query ≥ 1,000일 때만 같은 두 기준. 적으면 건너뛰고 보고한다(새 패치 직후)
- 승률 V·ESS는 행동 모델 결과(propensity)가 있으면 보고만 한다. 판정에 넣는 것은 몇 회차 본 뒤 승인받는다

종료 코드: 0 통과, 1 기준 미달, 2 표본 부족(game test 전체).
"""
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from dfgg_ltr.dataset import Dataset, QueryMeta, load_dataset, load_query_meta
from dfgg_ltr.evaluate import load_propensities, model_scores
from dfgg_ltr.metrics import evaluate_ranking, policy_value, policy_value_difference, top1_matches
from dfgg_ltr.propensity import select_queries

EVALUATION_SPLIT = "game"
NDCG_TOLERANCE = 0.005
HIT_RATE_TOLERANCE = 0.01
DEFAULT_MIN_TOTAL_QUERIES = 5000
DEFAULT_MIN_LATEST_PATCH_QUERIES = 1000
INSUFFICIENT_PREFIX = "표본 부족"

PASSED = 0
REJECTED = 1
INSUFFICIENT_SAMPLE = 2


@dataclass
class SliceComparison:
    name: str
    queries: int
    current: dict
    candidate: dict
    failures: list[str] = field(default_factory=list)
    skipped: str | None = None
    win_rate: dict | None = None


def ranking_failures(current: dict, candidate: dict) -> list[str]:
    failures = []
    if candidate["ndcg@5"] < current["ndcg@5"] - NDCG_TOLERANCE:
        failures.append(f"NDCG@5 {candidate['ndcg@5']:.4f} < 현재 {current['ndcg@5']:.4f} − {NDCG_TOLERANCE}")
    if candidate["hit_rate@5"] < current["hit_rate@5"] - HIT_RATE_TOLERANCE:
        failures.append(
            f"HitRate@5 {candidate['hit_rate@5']:.4f} < 현재 {current['hit_rate@5']:.4f} − {HIT_RATE_TOLERANCE}")
    return failures


def judge_total(comparison: SliceComparison, min_queries: int) -> SliceComparison:
    """전체 평가셋은 표본이 모자라면 판정하지 않고 표본 부족으로 둔다."""
    if comparison.queries < min_queries:
        comparison.failures.append(f"{INSUFFICIENT_PREFIX}: test query {comparison.queries} < {min_queries}")
        return comparison
    comparison.failures.extend(ranking_failures(comparison.current, comparison.candidate))
    return comparison


def judge_latest_patch(comparison: SliceComparison, min_queries: int) -> SliceComparison:
    """최신 패치는 표본이 모자라면 건너뛴다. 새 패치 직후에 막으면 재학습 모델이 한동안 나가지 못한다."""
    if comparison.queries < min_queries:
        comparison.skipped = f"query {comparison.queries} < {min_queries}라 판정에서 뺐다"
        return comparison
    comparison.failures.extend(ranking_failures(comparison.current, comparison.candidate))
    return comparison


def win_rate_report(dataset: Dataset, propensity: np.ndarray, win: np.ndarray, match_ids: list[str],
                    current_scores: np.ndarray, candidate_scores: np.ndarray) -> dict:
    """보고 전용. 후보·현재의 V와 ESS, 같은 부트스트랩 표본으로 잰 ΔV."""
    current_matches = top1_matches(dataset.labels, current_scores, dataset.group)
    candidate_matches = top1_matches(dataset.labels, candidate_scores, dataset.group)
    current = policy_value(current_matches, propensity, win, match_ids)
    candidate = policy_value(candidate_matches, propensity, win, match_ids)
    difference = policy_value_difference(candidate_matches, current_matches, propensity, win, match_ids)
    return {
        "current_v": current.value, "current_ess": current.ess,
        "candidate_v": candidate.value, "candidate_ess": candidate.ess,
        "delta_v": difference["difference"], "delta_v_lower": difference["lower"],
        "delta_v_upper": difference["upper"], "observed_win": current.observed_win,
        "coverage": current.coverage,
    }


def exit_code(comparisons: list[SliceComparison]) -> int:
    failures = [failure for comparison in comparisons for failure in comparison.failures]
    if any(failure.startswith(INSUFFICIENT_PREFIX) for failure in failures):
        return INSUFFICIENT_SAMPLE
    if failures:
        return REJECTED
    return PASSED


def latest_patch(patches: list[str]) -> str:
    return max(set(patches), key=lambda patch: tuple(int(part) for part in patch.split(".")))


def compare(dataset: Dataset, meta: QueryMeta, latest: str, current_scores: np.ndarray,
            candidate_scores: np.ndarray, propensity: np.ndarray | None,
            min_total_queries: int = DEFAULT_MIN_TOTAL_QUERIES,
            min_latest_patch_queries: int = DEFAULT_MIN_LATEST_PATCH_QUERIES) -> list[SliceComparison]:
    """game test 전체와 그중 최신 패치 query를 각각 판정한다."""
    total = _slice("game test 전체", np.ones(len(dataset.group), dtype=bool), dataset, meta,
                   current_scores, candidate_scores, propensity)
    latest_mask = np.array([patch == latest for patch in meta.patches])
    latest_slice = _slice(f"game test 중 최신 패치 {latest}", latest_mask, dataset, meta,
                          current_scores, candidate_scores, propensity)
    return [judge_total(total, min_total_queries), judge_latest_patch(latest_slice, min_latest_patch_queries)]


def _slice(name: str, query_mask: np.ndarray, dataset: Dataset, meta: QueryMeta, current_scores: np.ndarray,
           candidate_scores: np.ndarray, propensity: np.ndarray | None) -> SliceComparison:
    subset = select_queries(dataset, query_mask)
    row_mask = np.repeat(query_mask, dataset.group)
    current = current_scores[row_mask]
    candidate = candidate_scores[row_mask]
    comparison = SliceComparison(
        name=name, queries=int(query_mask.sum()),
        current=_ranking(subset, current), candidate=_ranking(subset, candidate))
    if propensity is not None and subset.group.size > 0:
        comparison.win_rate = win_rate_report(
            subset, propensity[query_mask], meta.win[query_mask],
            list(np.asarray(meta.match_ids)[query_mask]), current, candidate)
    return comparison


def _ranking(dataset: Dataset, scores: np.ndarray) -> dict:
    if dataset.group.size == 0:
        return {"ndcg@5": float("nan"), "hit_rate@5": float("nan"), "queries": 0}
    return evaluate_ranking(dataset.labels, scores, list(dataset.group))


def render_summary(candidate_name: str, current_name: str, comparisons: list[SliceComparison], code: int) -> str:
    """Slack에 그대로 올릴 수 있는 짧은 요약."""
    verdicts = {PASSED: "통과", REJECTED: "기준 미달", INSUFFICIENT_SAMPLE: "표본 부족"}
    lines = [f"모델 비교: {candidate_name} vs 현재 {current_name} → *{verdicts[code]}*"]
    for comparison in comparisons:
        lines.append(
            f"• {comparison.name} (query {comparison.queries:,}): "
            f"NDCG@5 {comparison.candidate['ndcg@5']:.4f} (현재 {comparison.current['ndcg@5']:.4f}), "
            f"HitRate@5 {comparison.candidate['hit_rate@5']:.4f} (현재 {comparison.current['hit_rate@5']:.4f})")
        lines.append(_win_rate_line(comparison.win_rate))
        if comparison.skipped is not None:
            lines.append(f"  – 건너뜀: {comparison.skipped}")
        for failure in comparison.failures:
            lines.append(f"  ✗ {failure}")
    return "\n".join(lines)


def _win_rate_line(win_rate: dict | None) -> str:
    if win_rate is None:
        return "  승률 V: 보고 없음 (행동 모델 결과 또는 export의 승패가 없다)"
    return (f"  승률 V(보고만): {win_rate['candidate_v']:.4f} (현재 {win_rate['current_v']:.4f}), "
            f"ΔV {win_rate['delta_v'] * 100:+.2f}%p ({win_rate['delta_v_lower'] * 100:+.2f}, "
            f"{win_rate['delta_v_upper'] * 100:+.2f}), ESS {win_rate['candidate_ess']:,.0f} "
            f"(현재 {win_rate['current_ess']:,.0f})")


def load_propensity_if_present(propensity_dir: Path | None, dataset: Dataset, meta: QueryMeta) -> np.ndarray | None:
    """행동 모델 결과나 export의 승패가 없으면 V를 보고하지 않는다."""
    if propensity_dir is None:
        return None
    path = propensity_dir / f"propensity-{EVALUATION_SPLIT}.jsonl"
    if not path.exists() or np.isnan(meta.win).all():
        return None
    return load_propensities(path, dataset.qids)


def main() -> None:
    parser = argparse.ArgumentParser(description="후보 모델과 현재 운영 모델 비교 (U10)")
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--schema", type=Path, default=Path("data/feature_schema.json"))
    parser.add_argument("--current", type=Path, required=True, help="현재 운영 모델 JSON")
    parser.add_argument("--candidate", type=Path, required=True,
                        help="후보 모델 JSON. game split의 train으로만 학습한 모델이어야 한다")
    parser.add_argument("--min-total-queries", type=int, default=DEFAULT_MIN_TOTAL_QUERIES)
    parser.add_argument("--min-latest-patch-queries", type=int, default=DEFAULT_MIN_LATEST_PATCH_QUERIES)
    parser.add_argument("--propensity-dir", type=Path, default=None,
                        help="propensity-game.jsonl 폴더. 주면 승률 V를 보고에 넣는다(판정에는 쓰지 않음)")
    parser.add_argument("--out", type=Path, default=None, help="비교 결과 JSON")
    args = parser.parse_args()

    dataset = load_dataset(args.data, args.schema, EVALUATION_SPLIT, "test")
    meta = load_query_meta(args.data, EVALUATION_SPLIT, "test")
    train_meta = load_query_meta(args.data, EVALUATION_SPLIT, "train")
    current_model = json.loads(args.current.read_text(encoding="utf-8"))
    candidate_model = json.loads(args.candidate.read_text(encoding="utf-8"))

    comparisons = compare(
        dataset, meta, latest_patch(meta.patches + train_meta.patches),
        model_scores(current_model, dataset), model_scores(candidate_model, dataset),
        load_propensity_if_present(args.propensity_dir, dataset, meta),
        args.min_total_queries, args.min_latest_patch_queries)
    code = exit_code(comparisons)
    summary = render_summary(args.candidate.name, args.current.name, comparisons, code)
    print(summary)
    if args.out is not None:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps({
            "exit_code": code,
            "summary": summary,
            "slices": [comparison.__dict__ for comparison in comparisons],
        }, ensure_ascii=False, indent=2), encoding="utf-8")
    sys.exit(code)


if __name__ == "__main__":
    main()
