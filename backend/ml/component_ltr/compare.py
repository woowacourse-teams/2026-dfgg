"""동일 v5 Test 후보와 기존 v4 공통 query에서 모델/관계 Feature 제거 실험을 비교한다."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import lightgbm as lgb
import numpy as np
from component_ltr.dataset import load_dataset
from component_ltr.evaluate import evaluate
from component_ltr.feature_schema import FEATURE_NAMES
from dfgg_ltr.dataset import Dataset


def subset(dataset, group_indices):
    starts = np.concatenate(([0], np.cumsum(dataset.group)))
    indices = np.concatenate([np.arange(starts[i], starts[i + 1]) for i in group_indices])
    return Dataset(dataset.features[indices], dataset.labels[indices], dataset.group[group_indices],
                   [dataset.qids[i] for i in group_indices], dataset.feature_names, dataset.schema_fingerprint), indices


def identity(row):
    return (row['matchId'], row['participantId'], row['startPurchaseOrder'], row['endPurchaseOrder'],
            row.get('targetItemId', row.get('targetCoreId')))


def signature(row):
    return {tuple(sorted(c['itemQuantities'].items())): (c['label'], tuple(c['features'][:41])) for c in row['candidates']}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--data-dir', type=Path, required=True)
    parser.add_argument('--experiments', type=Path, required=True)
    parser.add_argument('--legacy-model', type=Path, required=True)
    parser.add_argument('--legacy-test', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    dataset = load_dataset(args.data_dir / 'test.jsonl')
    results, predictions = {}, {}
    for folder in sorted(args.experiments.iterdir()):
        if not (folder / 'model.json').exists():
            continue
        metadata = json.loads((folder / 'model.json').read_text())
        columns = [FEATURE_NAMES.index(name) for name in metadata['feature_names']]
        model = lgb.Booster(model_file=str(folder / 'booster.txt'))
        predictions[folder.name] = model.predict(dataset.features[:, columns])
        results[folder.name] = {'metrics': evaluate(dataset, predictions[folder.name]),
                                'featureCount': len(columns), 'groups': len(dataset.group),
                                'modelSha256': hashlib.sha256((folder / 'model.json').read_bytes()).hexdigest()}
    legacy = json.loads((args.legacy_model / 'model.json').read_text())
    expected = hashlib.sha256(json.dumps(['component-bundle-validation-v4', FEATURE_NAMES[:41]]).encode()).hexdigest()
    if legacy['feature_names'] != FEATURE_NAMES[:41] or legacy['schema_fingerprint'] != expected:
        raise ValueError('기존 v4 모델의 41열 계약이 다릅니다')
    predictions['legacy_v4'] = lgb.Booster(model_file=str(args.legacy_model / 'booster.txt')).predict(dataset.features[:, :41])
    legacy_manifest = json.loads((args.legacy_model / 'split_manifest.json').read_text())['matches']
    for qid in dataset.qids:
        entry = legacy_manifest.get(qid.split(':')[0])
        if entry is None or entry['split'] != 'test':
            raise ValueError('v5 Test 경기가 기존 모델 Test 분할과 다릅니다')
    reference_manifest = json.loads((args.legacy_test.parent / 'manifest.json').read_text())['matches']
    reference_note = ('EXACT_MODEL_DATASET_MANIFEST' if reference_manifest == legacy_manifest
                      else 'DIFFERENT_DATA_REVISION; match-level Test membership verified, common exact queries only')
    old_rows = {}
    with args.legacy_test.open() as source:
        for line in source:
            row = json.loads(line)
            if row['schemaVersion'] != 'component-bundle-validation-v4':
                raise ValueError('기존 Test 파일이 v4가 아닙니다')
            old_rows[identity(row)] = signature(row)
    common = []
    with (args.data_dir / 'test.jsonl').open() as source:
        for index, line in enumerate(source):
            row = json.loads(line)
            if old_rows.get(identity(row)) == signature(row):
                common.append(index)
    if not common:
        raise ValueError('같은 목표/후보/기존 Feature/label을 갖는 공통 Test query가 없습니다')
    shared, indices = subset(dataset, np.asarray(common))
    report = {'purpose': 'OBSERVED_TARGET_OFFLINE_COMPARISON_NOT_CORE_BRIDGE_HOLDOUT',
              'same_v5_test_queries': results,
              'common_v4_v5_queries': {'groups': len(common), 'condition': 'same match/purchase/target/candidate quantities/labels/41 feature values',
                    'metrics': {name: evaluate(shared, scores[indices]) for name, scores in predictions.items()}},
              'legacyReferenceTest': str(args.legacy_test),
              'legacyReferenceRevisionNote': reference_note,
              'legacyModelSha256': hashlib.sha256((args.legacy_model / 'model.json').read_bytes()).hexdigest()}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
