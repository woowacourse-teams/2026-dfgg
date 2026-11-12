import csv
import json

import lightgbm as lgb
import numpy as np
import pytest

from component_ltr.tier_weight_experiment import (QuerySplit, coverage, read_tiers,
                                                 stratify, main)
from dfgg_ltr.dataset import Dataset


def fixture_split():
    d = Dataset(np.zeros((9, 41)), np.asarray([0, 1, 1, 0, 0, 0, 1, 0, 0]),
                np.asarray([2, 3, 4]), ['a:1', 'b:1', 'c:1'], ['f'] * 41, 'test')
    return QuerySplit(d, [{'tier': t, 'position': p, 'targetItemId': i, 'targetKind': k, 'completedCoreCount': c}
                         for t, p, i, k, c in [('EMERALD', 'TOP', 1, 'CORE', 0),
                                              ('MASTER', 'SUPPORT', 2, 'SUPPORT_FINISHED', 2),
                                              ('CHALLENGER', 'BOTTOM', 3, 'TIER_TWO_BOOTS', 4)]])


def test_query_weights_repeat_across_unequal_group_sizes_and_reach_lightgbm():
    split, weights = fixture_split().training_policy('strong')
    assert weights.tolist() == [1, 1, 1.75, 1.75, 1.75, 3, 3, 3, 3]
    dataset = lgb.Dataset(split.dataset.features, label=split.dataset.labels,
                          group=split.dataset.group, weight=weights).construct()
    np.testing.assert_allclose(dataset.get_weight(), weights)
    assert dataset.get_group().tolist() == [2, 3, 4]


def test_cutoff_keeps_whole_queries_labels_and_original_order():
    original = fixture_split()
    selected, weights = original.training_policy('master_plus')
    assert selected.dataset.qids == ['b:1', 'c:1']
    assert selected.dataset.group.tolist() == [3, 4]
    assert selected.dataset.labels.tolist() == original.dataset.labels[2:].tolist()
    assert weights.tolist() == [1] * 7
    assert original.dataset.group.tolist() == [2, 3, 4]


def test_empty_cutoff_fails_before_training():
    split = fixture_split().select([0])
    with pytest.raises(ValueError, match='no training queries'):
        split.training_policy('master_plus')


def test_unweighted_metrics_and_empty_tier_are_explicit():
    overall, strata = stratify(fixture_split(), np.arange(9, dtype=float))
    assert overall['groupCount'] == 3
    assert overall['hit@1'] == pytest.approx(1 / 3)
    assert overall['mrr'] == pytest.approx((1 + 1 / 3 + 1 / 3) / 3)
    assert strata['tier']['DIAMOND']['groupCount'] == 0
    assert strata['tier']['DIAMOND']['hit@1'] is None
    assert strata['expert']['MASTER+']['groupCount'] == 2
    assert strata['core_stage']['3+']['groupCount'] == 1
    assert overall['ndcg@5'] > 0


def test_missing_stage_is_not_assumed_zero():
    split = fixture_split()
    split.metadata[0]['completedCoreCount'] = None
    _, strata = stratify(split, np.arange(9, dtype=float))
    assert 'core_stage' not in strata


def test_coverage_tracks_targets_lost_entirely_and_weight_invariance():
    split = fixture_split()
    universe = {1: 'CORE', 2: 'SUPPORT_FINISHED', 3: 'TIER_TWO_BOOTS'}
    baseline, _ = coverage(split, 'baseline', universe)
    selected, _ = split.training_policy('strong')
    strong, _ = coverage(selected, 'strong', universe)
    assert [r['groupCount'] for r in baseline] == [r['groupCount'] for r in strong]
    cutoff, _ = split.training_policy('master_plus')
    targets, summary = coverage(cutoff, 'master_plus', universe)
    assert targets[0]['groupCount'] == 0
    assert summary[0]['lostTargetCount'] == 1
    assert summary[0]['trainGroupCount'] == 2
    assert summary[0]['targetItemCount'] == 2


def write_tiers(path, rows):
    with path.open('w', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(['matchId', 'participantId', 'tier', 'tierSource', 'observedAt'])
        writer.writerows(rows)


def test_tier_sources_duplicates_and_timestamps_are_validated(tmp_path):
    path = tmp_path / 'tiers.csv'
    row = ['a', 1, 'MASTER', 'COLLECTION_COHORT', '']
    write_tiers(path, [row])
    with pytest.raises(ValueError, match='not player tier'):
        read_tiers(path)
    assert read_tiers(path, True)['a', 1]['tier'] == 'MASTER'
    write_tiers(path, [row, row])
    with pytest.raises(ValueError, match='Duplicate'):
        read_tiers(path, True)
    write_tiers(path, [['a', 1, 'MASTER', 'PLAYER_OBSERVED_RANK', '']])
    with pytest.raises(ValueError, match='observation time'):
        read_tiers(path)


def test_cohort_must_be_constant_within_match(tmp_path):
    path = tmp_path / 'tiers.csv'
    write_tiers(path, [['a', 1, 'MASTER', 'COLLECTION_COHORT', ''],
                       ['a', 2, 'EMERALD', 'COLLECTION_COHORT', '']])
    with pytest.raises(ValueError, match='cohort'):
        read_tiers(path, True)


def test_output_overwrite_is_refused(tmp_path):
    (tmp_path / 'existing').write_text('keep')
    with pytest.raises(SystemExit):
        main(['--data-dir', str(tmp_path), '--tier-csv', str(tmp_path / 'tiers.csv'), '--out-dir', str(tmp_path)])
    assert (tmp_path / 'existing').read_text() == 'keep'


def test_end_to_end_policies_share_evaluation_splits(tmp_path):
    from component_ltr.feature_schema import FEATURE_NAMES, SCHEMA_VERSION
    from component_ltr.tier_weight_experiment import TIERS, POLICIES
    data = tmp_path / 'data'
    data.mkdir()
    manifest, tier_rows = {}, []
    for split in ('train', 'validation', 'test'):
        rows = []
        for index, tier in enumerate(TIERS):
            match = f'{split}_{index}'
            manifest[match] = {'split': split, 'rankingGroups': 1}
            tier_rows.append([match, 1, tier, 'COLLECTION_COHORT', ''])
            rows.append({'schemaVersion': SCHEMA_VERSION, 'featureNames': FEATURE_NAMES,
                         'status': 'READY', 'targetSource': 'OBSERVED_FUTURE_TARGET_ITEM',
                         'matchId': match, 'participantId': 1, 'startPurchaseOrder': 1,
                         'endPurchaseOrder': 1, 'targetItemId': 3071, 'targetKind': 'CORE',
                         'position': 'TOP', 'completedCoreCount': 0, 'observedItems': {'1036': 1},
                         'candidates': [{'itemQuantities': {'1036': 1}, 'label': 1, 'features': [1.] * 46 + [0.] * (len(FEATURE_NAMES) - 46)},
                                        {'itemQuantities': {'1037': 1}, 'label': 0, 'features': [0.] * len(FEATURE_NAMES)}]})
        (data / f'{split}.jsonl').write_text('\n'.join(json.dumps(r) for r in rows) + '\n')
    (data / 'manifest.json').write_text(json.dumps({'matches': manifest}))
    tiers = tmp_path / 'tiers.csv'
    write_tiers(tiers, tier_rows)
    out = tmp_path / 'out'
    main(['--data-dir', str(data), '--tier-csv', str(tiers), '--out-dir', str(out),
          '--allow-cohort', '--rounds', '2'])
    for policy in POLICIES:
        e = json.loads((out / policy / 'evaluation.json').read_text())
        assert e['groups']['validation'] == e['groups']['test'] == 5
        assert e['evaluationWeighted'] is False
        assert e['model']['groupCount'] == 5
        assert json.loads((out / policy / 'model.json').read_text())['deployable'] is False
        assert (out / policy / 'split_manifest.json').read_bytes() == (data / 'manifest.json').read_bytes()
    assert json.loads((out / 'master_plus/evaluation.json').read_text())['groups']['train'] == 3
    assert len(list(csv.DictReader((out / 'tier_metrics.csv').open()))) == 25
    assert 'COHORT EXPERIMENT' in (out / 'report.md').read_text()
