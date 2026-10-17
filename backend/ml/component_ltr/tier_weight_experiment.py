"""Fixed-split, train-only tier weighting. No feature, candidate or label generation.

Tier CSV: matchId,participantId,tier,tierSource,observedAt
Sources: PLAYER_OBSERVED_RANK, MATCH_TIME_RANK, COLLECTION_COHORT.
Cohorts require an explicit opt-in and never represent player rank.
"""
from __future__ import annotations

import argparse
from collections import Counter
import csv
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import shlex
import shutil
import sys

import lightgbm as lgb
import numpy as np
from component_ltr.dataset import load_splits
from component_ltr.evaluate import evaluate
from component_ltr.feature_schema import FEATURE_NAMES, SCHEMA_VERSION
from dfgg_ltr.dataset import Dataset
from dfgg_ltr.metrics import ndcg_at_k
from dfgg_ltr.model_export import to_export_dict
from dfgg_ltr.reference_predict import predict_with_flat_trees
from dfgg_ltr.train import to_lgb_dataset

TIERS = ('EMERALD', 'DIAMOND', 'MASTER', 'GRANDMASTER', 'CHALLENGER')
ROLES = ('TOP', 'JUNGLE', 'MID', 'BOTTOM', 'SUPPORT')
KINDS = ('CORE', 'TIER_TWO_BOOTS', 'SUPPORT_FINISHED')
POLICIES = {
    'baseline': (1., 1., 1., 1., 1.),
    'mild': (1., 1.1, 1.25, 1.5, 1.75),
    'strong': (1., 1.25, 1.75, 2.5, 3.),
    'diamond_plus': (0., 1., 1., 1., 1.),
    'master_plus': (0., 0., 1., 1., 1.),
}
PARAMS = {'objective': 'lambdarank', 'metric': 'ndcg', 'ndcg_eval_at': [1, 3],
          'learning_rate': .05, 'num_leaves': 15, 'min_data_in_leaf': 50,
          'seed': 158, 'deterministic': True, 'force_col_wise': True,
          'num_threads': 4, 'verbosity': -1}


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')


def write_csv(path, rows, columns):
    with path.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)


def read_tiers(path, allow_cohort=False):
    tiers = {}
    with path.open(encoding='utf-8', newline='') as source:
        for row in csv.DictReader(source):
            key = (row['matchId'], int(row['participantId']))
            if key in tiers:
                raise ValueError(f'Duplicate participant tier: {key}')
            if row['tier'] not in TIERS:
                raise ValueError(f'Unsupported tier for {key}: {row["tier"]}')
            if row['tierSource'] not in ('PLAYER_OBSERVED_RANK', 'MATCH_TIME_RANK', 'COLLECTION_COHORT'):
                raise ValueError(f'Unverified tier source: {key}')
            if row['tierSource'] == 'COLLECTION_COHORT' and not allow_cohort:
                raise ValueError('Collection cohort is not player tier; use --allow-cohort for an exploratory run')
            if row['tierSource'] != 'COLLECTION_COHORT' and not row.get('observedAt'):
                raise ValueError(f'Rank observation time required: {key}')
            tiers[key] = row
    if not tiers:
        raise ValueError('Empty tier metadata')
    sources = {r['tierSource'] for r in tiers.values()}
    if 'COLLECTION_COHORT' in sources and len(sources) > 1:
        raise ValueError('Do not mix player ranks and collection cohorts')
    cohorts = {}
    for (match, _), row in tiers.items():
        if row['tierSource'] == 'COLLECTION_COHORT':
            if match in cohorts and cohorts[match] != row['tier']:
                raise ValueError(f'Inconsistent collection cohort within match: {match}')
            cohorts[match] = row['tier']
    return tiers


@dataclass(frozen=True)
class QuerySplit:
    dataset: Dataset
    metadata: list[dict]

    def select(self, indices):
        indices = np.asarray(indices, dtype=int)
        if not len(indices):
            raise ValueError('Policy leaves no training queries')
        boundaries = np.concatenate(([0], np.cumsum(self.dataset.group)))
        rows = np.concatenate([np.arange(boundaries[i], boundaries[i + 1]) for i in indices])
        d = self.dataset
        return QuerySplit(Dataset(d.features[rows], d.labels[rows], d.group[indices],
                                  [d.qids[i] for i in indices], d.feature_names, d.schema_fingerprint),
                          [self.metadata[i] for i in indices])

    def training_policy(self, name):
        weights = dict(zip(TIERS, POLICIES[name], strict=True))
        indices = [i for i, row in enumerate(self.metadata) if weights[row['tier']] > 0]
        selected = self.select(indices)
        # LightGBM accepts instance weights. Repeat one query weight across all its candidates.
        query_weights = [weights[r['tier']] for r in selected.metadata]
        return selected, np.repeat(query_weights, selected.dataset.group)


def load_query_splits(data_dir, out_dir, tiers):
    datasets = load_splits(data_dir, out_dir / 'dataset-validation.json')
    names = FEATURE_NAMES[:41]
    fingerprint = hashlib.sha256(json.dumps([SCHEMA_VERSION, names]).encode()).hexdigest()
    splits, audit = {}, {}
    for name, d in zip(('train', 'validation', 'test'), datasets, strict=True):
        metadata, missing = [], []
        with (data_dir / f'{name}.jsonl').open(encoding='utf-8') as source:
            for qid, line in zip(d.qids, source, strict=True):
                row = json.loads(line)
                expected = f"{row['matchId']}:{row['participantId']}:{row['startPurchaseOrder']}:{row['endPurchaseOrder']}:{row['targetItemId']}"
                if expected != qid:
                    raise ValueError('Metadata/query alignment failed')
                rank = tiers.get((row['matchId'], row['participantId']))
                if rank is None:
                    missing.append(qid)
                metadata.append({k: row.get(k) for k in ('targetItemId', 'targetKind', 'position', 'completedCoreCount')} | (rank or {}))
        audit[name] = {'groupCount': len(d.group), 'missingTierGroups': len(missing), 'examples': missing[:10]}
        splits[name] = QuerySplit(Dataset(d.features[:, :41], d.labels, d.group, d.qids, names, fingerprint), metadata)
    write_json(out_dir / 'tier-validation.json', audit)
    if any(a['missingTierGroups'] for a in audit.values()):
        raise ValueError('Tier missing in fixed splits; no queries silently excluded. See tier-validation.json')
    return splits


def query_metrics(dataset, scores):
    records, offset = [], 0
    for size in dataset.group:
        end = offset + int(size)
        group = Dataset(dataset.features[offset:end], dataset.labels[offset:end], np.asarray([size]), [],
                        dataset.feature_names, dataset.schema_fingerprint)
        metrics = evaluate(group, scores[offset:end])
        metrics['ndcg@5'] = ndcg_at_k(group.labels, scores[offset:end], 5)
        records.append(metrics)
        offset = end
    return records


def aggregate(records, indices):
    keys = ('hit@1', 'hit@3', 'mrr', 'ndcg@1', 'ndcg@3', 'ndcg@5')
    return {'groupCount': len(indices), **{k: float(np.mean([records[i][k] for i in indices])) if indices else None for k in keys}}


def stratify(split, scores):
    records = query_metrics(split.dataset, scores)
    metadata = split.metadata
    categories = {
        'tier': {t: [i for i, r in enumerate(metadata) if r['tier'] == t] for t in TIERS},
        'expert': {label: [i for i, r in enumerate(metadata) if r['tier'] in tiers]
                   for label, tiers in [('MASTER+', TIERS[2:]), ('GRANDMASTER+', TIERS[3:])]},
        'role': {role: [i for i, r in enumerate(metadata) if r['position'] == role] for role in (*ROLES, 'UNKNOWN')},
    }
    categories['role']['UNKNOWN'] = [i for i, r in enumerate(metadata) if r['position'] not in ROLES]
    # Never treat missing core-stage metadata as stage zero.
    stages = [r['completedCoreCount'] for r in metadata]
    if all(type(c) is int and c >= 0 for c in stages):
        categories['core_stage'] = {label: [i for i, c in enumerate(stages) if min(c, 3) == stage]
                                    for stage, label in enumerate(('0', '1', '2', '3+'))}
    return aggregate(records, list(range(len(records)))), {
        category: {label: aggregate(records, indices) for label, indices in buckets.items()}
        for category, buckets in categories.items()}


def coverage(split, policy, universe):
    counts = Counter(r['targetItemId'] for r in split.metadata)
    rows = [{'policy': policy, 'targetItemId': target, 'targetKind': kind, 'groupCount': counts[target]}
            for target, kind in sorted(universe.items())]
    summaries = []
    for kind in ('ALL', *KINDS):
        targets = [t for t, k in universe.items() if kind == 'ALL' or k == kind]
        observed = [counts[t] for t in targets if counts[t]]
        summaries.append({'policy': policy, 'targetKind': kind, 'trainGroupCount': sum(counts[t] for t in targets),
                          'targetItemCount': len(observed), 'lostTargetCount': sum(counts[t] == 0 for t in targets),
                          'medianGroupCount': float(np.median(observed)) if observed else None,
                          **{f'below{threshold}': sum(counts[t] < threshold for t in targets) for threshold in (10, 30, 50, 100, 300)}})
    return rows, summaries


def build_report(evaluations, coverage_rows, target_rows, plan):
    lines = ['# Component LTR tier weight experiment', '',
             'Label 1 = observed purchase; Label 0 = unobserved candidate. Neither denotes objective optimality.',
             'Train-only weighting/cutoff; all policies share untouched, unweighted validation/test queries.',
             'Feature contract: first 41 baseline columns; target relationship improvement columns excluded.',
             'Core stage counts retained distinct CORE items at query time, excluding undone purchases; not lifetime purchases. Missing stage metadata skips core-stage evaluation.',
             'Coverage sparsity includes zero-count targets from the baseline universe. Medians use observed targets.',
             'Tier sources: ' + ', '.join(plan['tierSources']),
             'Observation timestamps are saved in the input snapshot. PLAYER_OBSERVED_RANK is not historical match-time rank.',
             '', 'Command: `' + plan['command'] + '`', '',
             '| Policy | Test groups | Hit@1 | MASTER+ groups | MASTER+ Hit@1 | MASTER+ Hit@3 | MASTER+ MRR |',
             '|---|---:|---:|---:|---:|---:|---:|']
    def fmt(value):
        return 'N/A' if value is None else f'{value:.6f}'
    for name, e in evaluations.items():
        expert = e['stratified_test']['expert']['MASTER+']
        lines.append(f"| {name} | {e['model']['groupCount']} | {fmt(e['model']['hit@1'])} | {expert['groupCount']} | {fmt(expert['hit@1'])} | {fmt(expert['hit@3'])} | {fmt(expert['mrr'])} |")
    baseline = evaluations['baseline']
    for question, policies in [('Q1: MASTER+ changes against baseline', ('mild', 'strong')),
                               ('Q2: Overall test trade-off', ('mild', 'strong')),
                               ('Q3: Strong versus mild', ('strong',))]:
        lines.extend(['', '## ' + question])
        reference = evaluations['mild'] if question.startswith('Q3') else baseline
        for name in policies:
            metrics = evaluations[name]['model'] if question.startswith('Q2') else evaluations[name]['stratified_test']['expert']['MASTER+']
            ref = reference['model'] if question.startswith('Q2') else reference['stratified_test']['expert']['MASTER+']
            lines.append(name + ': ' + ', '.join(k + ' delta=' + fmt(None if metrics[k] is None or ref[k] is None else metrics[k] - ref[k]) for k in ('hit@1', 'hit@3', 'mrr')))
    lines.extend(['', '## Q4: Coverage retention', 'Baseline, mild and strong have identical targets and group counts (asserted).'])
    for r in coverage_rows:
        if r['targetKind'] == 'ALL':
            lines.append(f"{r['policy']}: groups={r['trainGroupCount']}, targets={r['targetItemCount']}, median={r['medianGroupCount']}, lost={r['lostTargetCount']}.")
    lines.extend(['', '## Q5: Targets made sparse by cutoff'])
    baseline_counts = {r['targetItemId']: r['groupCount'] for r in target_rows if r['policy'] == 'baseline'}
    for name in ('diamond_plus', 'master_plus'):
        lines.append(name + ':')
        lost = [str(r['targetItemId']) for r in target_rows if r['policy'] == name and r['groupCount'] == 0]
        lines.append('Completely lost targets: ' + (', '.join(lost) or 'none'))
        for threshold in (10, 30, 50, 100, 300):
            transitions = [f"{r['targetItemId']} ({baseline_counts[r['targetItemId']]}→{r['groupCount']})" for r in target_rows
                           if r['policy'] == name and r['groupCount'] < threshold <= baseline_counts[r['targetItemId']]]
            lines.append(f"- Newly below {threshold}: " + (', '.join(transitions) or 'none'))
    lines.extend(['', '## Q6: Boots and support finished items'])
    for r in coverage_rows:
        if r['targetKind'] in KINDS[1:]:
            lines.append(f"{r['policy']} / {r['targetKind']}: groups={r['trainGroupCount']}, targets={r['targetItemCount']}, lost={r['lostTargetCount']}.")
    support_groups = next(r['trainGroupCount'] for r in coverage_rows if r['policy'] == 'baseline' and r['targetKind'] == 'SUPPORT_FINISHED')
    if not support_groups:
        lines.append('Support finished items have zero baseline training queries. This dataset cannot evaluate support-finished-item coverage or weighting effects.')
    lines.extend(['', '## Q7: Tier, role and core-stage regressions', 'Descriptive regression flag: Hit@1 or MRR drops by at least 0.02. Group counts accompany every flag; no significance claim.'])
    for name, e in evaluations.items():
        if name == 'baseline':
            continue
        flagged = False
        for category in ('tier', 'role', 'core_stage'):
            buckets = e['stratified_test'].get(category, {})
            observed = [(m['hit@1'] - baseline['stratified_test'][category][label]['hit@1'], label, m['groupCount']) for label, m in buckets.items() if m['groupCount']]
            if observed:
                delta, label, count = min(observed)
                lines.append(f'{name}/{category}: lowest Hit@1 delta={delta:.6f}, stratum={label}, n={count}.')
            for label, metrics in e['stratified_test'].get(category, {}).items():
                ref = baseline['stratified_test'][category][label]
                for key in ('hit@1', 'mrr'):
                    if metrics[key] is not None and metrics[key] - ref[key] <= -.02:
                        lines.append(f"{name}/{category}/{label}: n={metrics['groupCount']}, {key} delta={metrics[key]-ref[key]:.6f}")
                        flagged = True
        if not flagged:
            lines.append(name + ': no regression crossing the descriptive threshold.')
    experts = {n: e['stratified_test']['expert']['MASTER+']['hit@1'] for n, e in evaluations.items()}
    lines.extend(['', '## Q8: Candidate policy, not a service decision'])
    if all(v is not None for v in experts.values()):
        best = max(experts, key=experts.get)
        if best == 'baseline':
            lines.append('Equal weight is the most reasonable retention candidate in this run: weighting/cutoffs do not improve MASTER+ Hit@1 over baseline, while baseline retains all target coverage. Mild may still merit repeat experiments if Hit@3 is prioritized. Strong has no demonstrated advantage.')
        lines.append(f'Highest descriptive MASTER+ Hit@1: {best}. Compare overall/stratum losses and cutoff coverage above before choosing a candidate. No final production policy is selected; test-set selection needs fresh confirmation data.')
    else:
        lines.append('MASTER+ sample unavailable; no expert policy conclusion is supported.')
    if 'COLLECTION_COHORT' in plan['tierSources']:
        lines.append('COHORT EXPERIMENT (user-confirmed tier definition): strata represent match collection cohorts, not participant skill. Q1–Q3/Q8 assess collection-cohort behavior; they cannot establish individual player-tier effects.')
    lines.extend(['', 'Reproducibility: experiment-plan.json records input hashes, library versions, weights, seed, split and feature contract. Early stopping uses identical unweighted validation and patience=30; fitted iteration counts can differ.'])
    return '\n'.join(lines) + '\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', type=Path, required=True)
    parser.add_argument('--tier-csv', type=Path, required=True)
    parser.add_argument('--out-dir', type=Path, required=True)
    parser.add_argument('--rounds', type=int, default=1200)
    parser.add_argument('--allow-cohort', action='store_true')
    args = parser.parse_args(argv)
    if args.rounds <= 0:
        parser.error('--rounds must be positive')
    if args.out_dir.exists() and any(args.out_dir.iterdir()):
        parser.error('Output directory must be empty; existing results will not be overwritten')
    tiers = read_tiers(args.tier_csv, args.allow_cohort)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    splits = load_query_splits(args.data_dir, args.out_dir, tiers)
    train, valid, test = (splits[s] for s in ('train', 'validation', 'test'))
    prepared = {p: train.training_policy(p) for p in POLICIES}
    universe = {}
    for row in train.metadata:
        target, kind = row['targetItemId'], row['targetKind']
        if kind not in KINDS or target in universe and universe[target] != kind:
            raise ValueError('Unknown or inconsistent target kind')
        universe[target] = kind
    plan = {'command': shlex.join([sys.executable, '-m', 'component_ltr.tier_weight_experiment', *(argv if argv is not None else sys.argv[1:])]),
            'policies': {p: dict(zip(TIERS, w, strict=True)) for p, w in POLICIES.items()}, 'params': PARAMS,
            'rounds': args.rounds, 'earlyStoppingPatience': 30, 'featureNames': train.dataset.feature_names,
            'schemaFingerprint': train.dataset.schema_fingerprint, 'tierSources': sorted({r['tierSource'] for r in tiers.values()}),
            'versions': {'lightgbm': lgb.__version__, 'numpy': np.__version__, 'python': sys.version},
            'inputHashes': {str(p.resolve()): sha256(p) for p in [args.tier_csv, *(args.data_dir / f'{s}.jsonl' for s in splits), args.data_dir / 'manifest.json']}}
    write_json(args.out_dir / 'experiment-plan.json', plan)
    shutil.copyfile(args.tier_csv, args.out_dir / 'tier_snapshot.csv')
    evaluations, target_rows, coverage_rows = {}, [], []
    for name, (selected, weights) in prepared.items():
        print('TRAIN ' + name, flush=True)
        directory = args.out_dir / name
        directory.mkdir()
        shutil.copyfile(args.data_dir / 'manifest.json', directory / 'split_manifest.json')
        training = to_lgb_dataset(selected.dataset).set_weight(weights)
        history = {}
        booster = lgb.train(PARAMS, training, num_boost_round=args.rounds,
                            valid_sets=[to_lgb_dataset(valid.dataset, reference=training)],
                            callbacks=[lgb.early_stopping(30), lgb.record_evaluation(history), lgb.log_evaluation(50)])
        model, strata = stratify(test, booster.predict(test.dataset.features))
        valid_metrics, valid_strata = stratify(valid, booster.predict(valid.dataset.features))
        evaluation = {'policy': name, 'best_iteration': booster.best_iteration, 'model': model,
                      'validation': valid_metrics, 'stratified_test': strata, 'stratified_validation': valid_strata,
                      'groups': {'train': len(selected.metadata), 'validation': len(valid.metadata), 'test': len(test.metadata)},
                      'params': PARAMS, 'evaluationWeighted': False, 'history': history}
        exported = to_export_dict(booster, selected.dataset.feature_names, selected.dataset.schema_fingerprint)
        exported.update({'model_version': 'component-ltr-tier-weight-baseline41', 'deployable': False,
                         'target_source': 'OBSERVED_FUTURE_TARGET_ITEM'})
        sample = test.dataset.features[:100]
        np.testing.assert_allclose([predict_with_flat_trees(exported['trees'], r) for r in sample], booster.predict(sample), rtol=1e-10, atol=1e-10)
        write_json(directory / 'model.json', exported)
        write_json(directory / 'evaluation.json', evaluation)
        booster.save_model(str(directory / 'booster.txt'))
        evaluations[name] = evaluation
        targets, summary = coverage(selected, name, universe)
        target_rows.extend(targets)
        coverage_rows.extend(summary)
    for name in ('mild', 'strong'):
        assert [(r['targetItemId'], r['groupCount']) for r in target_rows if r['policy'] == name] == [(r['targetItemId'], r['groupCount']) for r in target_rows if r['policy'] == 'baseline']
    metrics = ['groupCount', 'hit@1', 'hit@3', 'mrr', 'ndcg@1', 'ndcg@3', 'ndcg@5']
    write_csv(args.out_dir / 'summary.csv', [{'policy': p, 'trainGroupCount': e['groups']['train'], 'bestIteration': e['best_iteration'], **e['model']} for p, e in evaluations.items()], ['policy', 'trainGroupCount', 'bestIteration', *metrics])
    for category in ('tier', 'expert', 'role', 'core_stage'):
        rows = [{'policy': p, category: label, **m} for p, e in evaluations.items() for label, m in e['stratified_test'].get(category, {}).items()]
        write_csv(args.out_dir / f'{category}_metrics.csv', rows, ['policy', category, *metrics])
    write_csv(args.out_dir / 'target_coverage.csv', coverage_rows, ['policy', 'targetKind', 'trainGroupCount', 'targetItemCount', 'lostTargetCount', 'medianGroupCount', *(f'below{t}' for t in (10, 30, 50, 100, 300))])
    write_csv(args.out_dir / 'target_sparsity.csv', target_rows, ['policy', 'targetItemId', 'targetKind', 'groupCount'])
    (args.out_dir / 'report.md').write_text(build_report(evaluations, coverage_rows, target_rows, plan), encoding='utf-8')


if __name__ == '__main__':
    main()
