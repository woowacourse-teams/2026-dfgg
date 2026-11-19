"""Summarize independent per-Core replay branches, with explicit denominators and leakage scope."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import csv
import json
from pathlib import Path
import numpy as np


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n")


def write_csv(path, rows, fields=None):
    rows = list(rows)
    with path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields or list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def ratio(numerator, denominator):
    return numerator / denominator if denominator else None


def rank_metrics(rows, rank_field):
    ranks = [r.get(rank_field) for r in rows]
    n = len(ranks)
    present = sum(rank is not None for rank in ranks)
    hit1 = sum(rank == 1 for rank in ranks)
    hit3 = sum(rank is not None and rank <= 3 for rank in ranks)
    return {"denominator": n, "candidatePresentCount": present, "candidateRecall": ratio(present, n),
            "hit1Count": hit1, "hit@1": ratio(hit1, n), "hit3Count": hit3, "hit@3": ratio(hit3, n),
            "mrr": ratio(sum(1 / rank for rank in ranks if rank is not None), n)}


def availability(branches):
    n = len(branches)
    one = sum(b["candidateCount"] >= 1 for b in branches)
    two = sum(b["candidateCount"] >= 2 for b in branches)
    errors = sum(b["candidateGenerationStatus"].endswith("FAILED") for b in branches)
    return {"denominator": n, "candidateGe1Count": one, "candidateGe1Rate": ratio(one, n),
            "candidateGe2Count": two, "candidateGe2Rate": ratio(two, n),
            "noCandidateCount": n - one, "noCandidateRate": ratio(n - one, n),
            "exceptionCount": errors, "exceptionRate": ratio(errors, n)}


def summarize(rows):
    covered = [r for r in rows if r.get("observedCoreRank") is not None]
    branches = [b for r in rows for b in r["corePredictions"]]
    result = {
        "scope": "PRODUCTION_SNAPSHOT_DIAGNOSTIC_NOT_INDEPENDENT_HOLDOUT",
        "groups": len(rows), "matches": len({r["matchId"] for r in rows}),
        "statisticsOverlapGroups": sum(r["statisticsContainsMatch"] for r in rows),
        "statisticsOverlapMatches": len({r["matchId"] for r in rows if r["statisticsContainsMatch"]}),
        "coreRecall": {str(k): {"numerator": sum(r.get("observedCoreRank") is not None and r["observedCoreRank"] <= k for r in rows),
                               "denominator": len(rows)} for k in (1, 3, 5)},
        "candidateAvailability": availability(branches),
        "oracleAll": rank_metrics(rows, "oracleObservedRank"),
        "oracleCoveredSubset": rank_metrics(covered, "oracleObservedRank"),
        "conditionalComponent": rank_metrics(covered, "observedPurchaseComponentRank"),
        "endToEnd": rank_metrics(rows, "observedPurchaseComponentRank"),
        "top5LengthCounts": dict(Counter(len(r["corePredictions"]) for r in rows)),
        "predictedPurchaseTypes": dict(Counter(b["purchaseType"] for b in branches)),
        "oracleExportMismatchCount": sum(not r.get("oracleMatchesExportedCandidates", False) for r in rows),
        "pairedBranchMismatchCount": 0,
    }
    for metric in result["coreRecall"].values():
        metric["recall"] = ratio(metric["numerator"], metric["denominator"])
    for r in covered:
        observed = next(b for b in r["corePredictions"] if b["coreId"] == r["observedFutureCoreId"])
        if observed["componentPredictions"] != r["oracle"]["componentPredictions"]:
            result["pairedBranchMismatchCount"] += 1
    result["funnel"] = {
        "ALL_GROUPS": len(rows), "OBSERVED_CORE_IN_TOP5": len(covered),
        "OBSERVED_CORE_HAS_CANDIDATES": sum((r.get("observedCoreCandidateCount") or 0) > 0 for r in covered),
        "OBSERVED_PURCHASE_PRESENT": result["conditionalComponent"]["candidatePresentCount"],
        "COMPONENT_TOP1": result["endToEnd"]["hit1Count"],
    }
    return result


def ks_statistic(first, second):
    first, second = np.sort(first), np.sort(second)
    points = np.unique(np.concatenate((first, second)))
    return float(np.max(np.abs(np.searchsorted(first, points, side="right") / len(first)
                               - np.searchsorted(second, points, side="right") / len(second))))


def describe(values):
    return {"mean": float(np.mean(values)), **dict(zip(("p10", "p25", "median", "p75", "p90"),
            (float(v) for v in np.quantile(values, [.1, .25, .5, .75, .9]))))}


def state_values(vector, names, inventory, lower, upper, candidate_count, purchase_types, catalog):
    features = dict(zip(names, vector))
    numeric = {"gameTimeSeconds": features["game_time_minutes"] * 60,
               "inventorySlotCount": features["slots_before"], "goldRangeLower": lower, "goldRangeUpper": upper,
               "candidateCount": candidate_count,
               "heldCoreQuantity": sum(q for i, q in inventory.items() if purchase_types.get(str(i)) == "CORE"),
               "heldComponentQuantity": sum(q for i, q in inventory.items() if purchase_types.get(str(i)) == "COMPONENT")}
    numeric.update({k: v for k, v in features.items() if k.endswith("_before") and k != "slots_before"})
    role = next((p.upper() for p in ("top", "jungle", "mid", "bottom", "support", "unknown")
                 if features["position_" + p] == 1), "UNKNOWN")
    tags = {tag for i in inventory for tag in catalog["data"].get(str(i), {}).get("tags", [])}
    categories = {"role": {role}, "heldItemId": set(map(str, inventory)), "heldItemTag": tags}
    return numeric, categories


def compare_distributions(rows, train_path, metadata, catalog, output):
    # This is descriptive aggregation of existing feature values, not a second Feature Builder.
    names = metadata["featureNames"]
    types = metadata["purchaseTypes"]
    train, predicted, train_early = defaultdict(list), defaultdict(list), defaultdict(list)
    for line in train_path.open():
        row = json.loads(line)
        state = state_values(row["candidates"][0]["features"], names, row["inventoryBefore"],
                             row["goldLower"], row["goldUpper"], len(row["candidates"]), types, catalog)
        train[row.get("targetItemId", row.get("targetCoreId"))].append(state)
        if all(types.get(str(i)) not in ("CORE", "BOOTS") for i in row["inventoryBefore"]):
            train_early[row.get("targetItemId", row.get("targetCoreId"))].append(state)
    for row in rows:
        if not row.get("oracle", {}).get("componentPredictions"):
            raise ValueError("Replay state features missing; do not silently drop distribution rows")
        vector = row["oracle"]["componentPredictions"][0]["candidateFeatures"]
        for branch in row["corePredictions"]:
            state = state_values(vector, names, row["inventoryBefore"], row["goldRangeLower"], row["goldRangeUpper"],
                                 branch["candidateCount"], types, catalog)
            predicted[branch["coreId"]].append(state)
    detailed, shifts, core_summary = [], [], []
    for core in sorted(set(train) | set(predicted)):
        name = catalog["data"].get(str(core), {}).get("name", str(core))
        core_summary.append({"coreId": core, "name": name, "purchaseType": types.get(str(core)),
                             "trainN": len(train[core]), "trainEarlyInventoryN": len(train_early[core]),
                             "predictedN": len(predicted[core])})
        for comparison, reference in (("ALL_TRAIN", train[core]), ("TRAIN_NO_CORE_OR_BOOTS_IN_INVENTORY", train_early[core])):
            current = predicted[core]
            if not reference or not current:
                shifts.append(dict(coreId=core, name=name, comparison=comparison, feature="ALL",
                                   statistic="NO_TRAIN_SUPPORT" if not reference else "NO_PREDICTED_SUPPORT",
                                   value=None, trainN=len(reference), predictedN=len(current)))
                continue
            for feature in reference[0][0]:
                first, second = [s[0][feature] for s in reference], [s[0][feature] for s in current]
                a, b = describe(first), describe(second)
                for statistic in a:
                    detailed.append(dict(coreId=core, name=name, comparison=comparison, feature=feature, statistic=statistic,
                                         trainN=len(first), predictedN=len(second), trainValue=a[statistic],
                                         predictedValue=b[statistic], difference=b[statistic] - a[statistic]))
                shifts.append(dict(coreId=core, name=name, comparison=comparison, feature=feature, statistic="KS",
                                   value=ks_statistic(first, second), trainN=len(first), predictedN=len(second)))
            for feature in reference[0][1]:
                a = Counter(value for state in reference for value in state[1][feature])
                b = Counter(value for state in current for value in state[1][feature])
                for category in sorted(set(a) | set(b)):
                    av, bv = a[category] / len(reference), b[category] / len(current)
                    detailed.append(dict(coreId=core, name=name, comparison=comparison, feature=feature,
                                         statistic=category, trainN=len(reference), predictedN=len(current),
                                         trainValue=av, predictedValue=bv, difference=bv - av))
                if feature == "role":
                    tvd = sum(abs(a[c] / len(reference) - b[c] / len(current)) for c in set(a) | set(b)) / 2
                    shifts.append(dict(coreId=core, name=name, comparison=comparison, feature=feature, statistic="TVD",
                                       value=tvd, trainN=len(reference), predictedN=len(current)))
    write_csv(output / "distribution_shift.csv", detailed)
    write_csv(output / "distribution_distances.csv", shifts)
    write_csv(output / "core_distribution_counts.csv", core_summary)
    return {"predictedBranchesWithoutTrainingTarget": sum(r["predictedN"] for r in core_summary if not r["trainN"]),
            "predictedTargetsWithoutTrainingTarget": [r for r in core_summary if not r["trainN"] and r["predictedN"]],
            "predictedTargetCount": sum(bool(states) for states in predicted.values()), "distributionRows": len(detailed)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replay", type=Path, required=True)
    parser.add_argument("--train", type=Path, required=True)
    parser.add_argument("--catalog", type=Path, required=True)
    args = parser.parse_args()
    output = args.replay
    metadata = json.loads((output / "replay_metadata.json").read_text())
    rows = [json.loads(line) for line in (output / "raw_results.jsonl").open()]
    if len(rows) != metadata["selectedGroups"] or not rows:
        raise ValueError("Incomplete replay output")
    ids = {(r["matchId"], r["participantId"], r["purchaseIndex"]) for r in rows}
    if len(ids) != len(rows):
        raise ValueError("Duplicate replay groups")
    summary = summarize(rows)
    summary["metadata"] = {k: v for k, v in metadata.items() if k != "purchaseTypes"}
    if summary["oracleExportMismatchCount"] or summary["pairedBranchMismatchCount"]:
        raise ValueError("Oracle/service parity mismatch; investigate before reporting metrics")
    write_csv(output / "core_recall.csv", [dict(k=k, **v) for k, v in summary["coreRecall"].items()])
    metric_rows = []
    for scope in ("oracleAll", "oracleCoveredSubset", "conditionalComponent", "endToEnd"):
        metric_rows.append({"scope": scope, **summary[scope]})
    write_csv(output / "component_metrics.csv", metric_rows)
    branches = [b for row in rows for b in row["corePredictions"]]
    availability_rows = [{"scope": "ALL", "key": "ALL", **availability(branches)}]
    for scope, field in (("RANK", "rank"), ("CORE", "coreId"), ("PURCHASE_TYPE", "purchaseType")):
        grouped = defaultdict(list)
        for branch in branches:
            grouped[branch[field]].append(branch)
        availability_rows.extend({"scope": scope, "key": key, **availability(grouped[key])} for key in sorted(grouped))
    write_csv(output / "candidate_availability.csv", availability_rows)
    failures = Counter(("GROUP_FIRST", r.get("firstFailureReason") or "SUCCESS") for r in rows)
    failures.update(("BRANCH_STATUS", b["candidateGenerationStatus"]) for b in branches)
    failures.update(("BRANCH_DETAIL", b["failureReason"]) for b in branches if b.get("failureReason"))
    write_csv(output / "failure_reasons.csv", [{"scope": scope, "reason": reason, "count": count}
                                               for (scope, reason), count in sorted(failures.items())])
    write_csv(output / "funnel.csv", [{"stage": stage, "groups": count, "allGroupRate": count / len(rows)}
                                     for stage, count in summary["funnel"].items()])
    catalog = json.loads(args.catalog.read_text())
    summary["distribution"] = compare_distributions(rows, args.train, metadata, catalog, output)
    write_json(output / "summary.json", summary)
    print(json.dumps({k: v for k, v in summary.items() if k not in ("metadata", "distribution")}, indent=2))


if __name__ == "__main__":
    main()
