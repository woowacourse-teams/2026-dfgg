"""Compare frozen v1 to target-path baseline on identical matches and original test queries."""
import argparse
import gzip
import json
from collections import Counter, defaultdict
from itertools import zip_longest
from pathlib import Path
import lightgbm as lgb
import numpy as np


def qid(row):
    return tuple(row[k] for k in ('matchId', 'participantId', 'startPurchaseOrder', 'endPurchaseOrder', 'targetItemId'))


def signature(candidate):
    return tuple(sorted(candidate['itemQuantities'].items()))


def metrics(rows, scores):
    buckets = defaultdict(list)
    offset = 0
    for row in rows:
        size = len(row['candidates'])
        order = np.argsort(-scores[offset:offset + size], kind='stable')
        positive = next(i for i, c in enumerate(row['candidates']) if c['label'] == 1)
        rank = int(np.flatnonzero(order == positive)[0]) + 1
        source = row['candidates'][positive].get('candidateSource', 'TARGET_PATH')
        for key in ('ALL', source):
            buckets[key].append((rank == 1, rank <= 3, 1 / rank))
        offset += size
    assert offset == len(scores)
    return {key: dict(groups=len(values), **dict(zip(('hit@1', 'hit@3', 'mrr'), np.mean(values, axis=0).tolist())))
            for key, values in buckets.items()}


def score(model, rows):
    return model.predict(np.asarray([c['features'] for r in rows for c in r['candidates']]), num_threads=4)


def evaluate(baseline, extended, baseline_model, model, output):
    manifest = json.loads((extended / 'manifest.json').read_text())['matches']
    old_manifest = json.loads((baseline / 'manifest.json').read_text())['matches']
    assert {m: v['split'] for m, v in manifest.items()} == {m: v['split'] for m, v in old_manifest.items()}
    counts = defaultdict(Counter)
    sizes = defaultdict(list)
    comparable = {'READY', 'OBSERVED_BUNDLE_MISSING', 'OBSERVED_COST_MISMATCH', 'OBSERVED_COST_NOT_CONFIRMED', 'OBSERVED_COST_OUTSIDE_GOLD_RANGE'}
    with (baseline / 'all.jsonl').open() as before, gzip.open(extended / 'all.jsonl.gz', 'rt') as after:
        for old_line, new_line in zip_longest(before, after):
            assert old_line and new_line
            old, new = json.loads(old_line), json.loads(new_line)
            assert qid(old) == qid(new)
            prior = {signature(c): c for c in old['candidates']}
            current = {signature(c): c for c in new['candidates']}
            assert prior.keys() <= current.keys(), qid(old)
            for key, c in prior.items():
                assert c['features'] == current[key]['features'], qid(old)
                assert current[key]['candidateSource'] == 'TARGET_PATH'
            split = manifest[old['matchId']]['split']
            for scope in ('all', split):
                for variant, row in (('baseline', old), ('preparation', new)):
                    key = scope + '/' + variant
                    counts[key]['bundles'] += 1
                    counts[key]['ready'] += int(row['status'] == 'READY')
                    counts[key]['usable'] += int(row['status'] == 'READY' and len(row['candidates']) > 1)
                    if row['status'] in comparable:
                        counts[key]['comparable'] += 1
                        counts[key]['recalled'] += int(any(c['itemQuantities'] == row['observedItems'] for c in row['candidates']))
                        sizes[key].append(len(row['candidates']))
                    if row['status'] in {'READY', 'OBSERVED_BUNDLE_MISSING', 'OBSERVED_COST_MISMATCH'}:
                        counts[key]['confirmedCostComparable'] += 1
                        counts[key]['confirmedCostRecalled'] += int(any(c['itemQuantities'] == row['observedItems'] for c in row['candidates']))
                    counts[key]['queriesWithPreparation'] += int(any(c.get('candidateSource') == 'PREPARATION' for c in row['candidates']))
    candidates = {}
    for key, c in counts.items():
        values = sizes[key]
        candidates[key] = dict(c, recall=c['recalled'] / c['comparable'], confirmedCostRecall=c['confirmedCostRecalled'] / c['confirmedCostComparable'], candidateSize=dict(
            mean=float(np.mean(values)), p50=float(np.percentile(values, 50)), p90=float(np.percentile(values, 90)),
            p95=float(np.percentile(values, 95)), max=max(values)))
    old_rows = [json.loads(line) for line in (baseline / 'test.jsonl').open()]
    new_rows = [json.loads(line) for line in (extended / 'test.jsonl').open()]
    old_ids = {qid(r) for r in old_rows}
    common = [r for r in new_rows if qid(r) in old_ids]
    assert len(common) == len(old_rows)
    old_booster, new_booster = lgb.Booster(model_file=str(baseline_model)), lgb.Booster(model_file=str(model))
    ranking = {
        'baseline_original_queries_original_candidates': metrics(old_rows, score(old_booster, old_rows)),
        'v1_original_queries_original_candidates': metrics(old_rows, score(new_booster, old_rows)),
        'baseline_original_queries_extended_candidates': metrics(common, score(old_booster, common)),
        'v1_original_queries_extended_candidates': metrics(common, score(new_booster, common)),
        'v1_all_extended_queries': metrics(new_rows, score(new_booster, new_rows)),
        'v1_new_usable_queries': metrics([r for r in new_rows if qid(r) not in old_ids], score(new_booster, [r for r in new_rows if qid(r) not in old_ids])),
    }
    output.mkdir(parents=True, exist_ok=True)
    result = dict(candidates=candidates, ranking=ranking, existingCandidateFeaturesUnchanged=True,
                  sizeDenominator='candidate-comparable bundles, including empty sets', thresholdsTunedOnTest=False)
    (output / 'evaluation.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    for name in ('baseline', 'extended', 'baseline-model', 'model', 'output'):
        p.add_argument('--' + name, type=Path, required=True)
    a = p.parse_args()
    evaluate(a.baseline, a.extended, a.baseline_model, a.model, a.output)
