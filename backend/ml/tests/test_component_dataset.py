"""최소 계약 검사: 후보 순서/그룹/관측 구매 Label/스키마 오류가 조용히 학습되지 않도록 한다."""
import math
import json
import pytest
from component_ltr.dataset import load_dataset
from component_ltr.feature_schema import FEATURE_NAMES, SCHEMA_VERSION, SCHEMA_FINGERPRINT


def test_bundle_dataset_contract(tmp_path):
    row = {"schemaVersion": SCHEMA_VERSION, "featureNames": FEATURE_NAMES, "status": "READY",
           "targetSource": "OBSERVED_FUTURE_TARGET_ITEM", "matchId": "KR_1", "participantId": 1, "targetItemId": 3071,
           "startPurchaseOrder": 1, "endPurchaseOrder": 2, "observedItems": {"1036": 2},
           "candidates": [{"itemQuantities": {"1036": 2}, "label": 1, "features": [700, 2, 1, 0, 2] + [0] * (len(FEATURE_NAMES) - 5)},
                          {"itemQuantities": {"1036": 1}, "label": 0, "features": [350, 1, 1, 0, 1] + [0] * (len(FEATURE_NAMES) - 5)}]}
    path = tmp_path / "train.jsonl"
    path.write_text(json.dumps(row) + '\n')
    dataset = load_dataset(path)
    assert dataset.group.tolist() == [2]
    assert dataset.labels.tolist() == [1, 0]
    assert dataset.features.shape == (2, len(FEATURE_NAMES))
    # Java 모델 로더의 계약 지문과 동일해야 한다.
    assert dataset.schema_fingerprint == SCHEMA_FINGERPRINT == (
        '6f287c3c7dadb29c739cde1de020523db5136e80a29c71388523e47e2a7e49b3'
    )
    row['candidates'][0]['label'] = 0
    path.write_text(json.dumps(row) + '\n')
    with pytest.raises(ValueError, match='정답'):
        load_dataset(path)


def test_validation_diagnostics_identifies_indistinguishable_candidates(tmp_path):
    """관측 구매와 벡터가 같은 대안의 Top-1 선택을 Feature 구분 한계로 따로 집계한다."""
    import numpy as np
    from dfgg_ltr.dataset import Dataset
    from component_ltr.analyze import diagnostics
    from component_ltr.evaluate import evaluate
    dataset = Dataset(np.zeros((2, len(FEATURE_NAMES))), np.asarray([0, 1]), np.asarray([2]),
                      ['KR_1:1:1:1'], FEATURE_NAMES, 'test-fingerprint')
    row = {'position': 'TOP', 'targetItemId': 3071, 'observedItems': {'1036': 2},
           'candidates': [{'itemQuantities': {'1037': 1}}, {'itemQuantities': {'1036': 2}}]}
    path = tmp_path / 'validation.jsonl'
    path.write_text(json.dumps(row) + '\n')
    scores = np.asarray([1.0, 1.0])
    report = diagnostics(dataset, scores, path)
    assert report['positiveFeatureCollisionGroups'] == 1
    assert report['positions']['TOP']['hit@1'] == 0.0
    assert report['wrongExamples'][0]['positiveFeatureCollision'] is True
    metrics = evaluate(dataset, scores)
    assert metrics['hit@1'] == 0.0
    assert metrics['hit@3'] == 1.0
    assert metrics['mrr'] == 0.5


@pytest.fixture
def ranking_row():
    return {"schemaVersion": SCHEMA_VERSION, "featureNames": FEATURE_NAMES.copy(), "status": "READY",
            "targetSource": "OBSERVED_FUTURE_TARGET_ITEM", "matchId": "KR_1", "participantId": 1, "targetItemId": 3071,
            "startPurchaseOrder": 1, "endPurchaseOrder": 2, "observedItems": {"1036": 2},
            "candidates": [{"itemQuantities": {"1036": 2}, "label": 1, "features": [700] + [0] * (len(FEATURE_NAMES) - 1)},
                           {"itemQuantities": {"1036": 1}, "label": 0, "features": [350] + [0] * (len(FEATURE_NAMES) - 1)}]}


@pytest.mark.parametrize("fault,reason", [
    ("schema", "SCHEMA_VERSION"), ("order", "FEATURE_ORDER"),
    ("status", "NOT_READY"), ("size", "QUERY_SIZE"),
    ("zero_positive", "POSITIVE_COUNT"), ("two_positive", "POSITIVE_COUNT"),
    ("null_label", "LABEL"), ("bool_label", "LABEL"),
    ("duplicate", "DUPLICATE_CANDIDATE"), ("short", "FEATURE_COUNT"),
    ("nan", "FEATURE_NUMERIC"), ("inf", "FEATURE_NUMERIC"),
    ("null_feature", "FEATURE_NUMERIC"), ("bool_feature", "FEATURE_NUMERIC"),
    ("wrong_positive", "POSITIVE_ITEMS"),
])
def test_invalid_groups_are_reported_and_never_silently_dropped(tmp_path, ranking_row, fault, reason):
    from component_ltr.dataset import DatasetValidationError
    row = ranking_row
    if fault == "schema": row["schemaVersion"] = "old"
    if fault == "order": row["featureNames"].reverse()
    if fault == "status": row["status"] = "BUNDLE_EXCLUDED"
    if fault == "size": row["candidates"] = row["candidates"][:1]
    if fault == "zero_positive": row["candidates"][0]["label"] = 0
    if fault == "two_positive": row["candidates"][1]["label"] = 1
    if fault == "null_label": row["candidates"][0]["label"] = None
    if fault == "bool_label": row["candidates"][0]["label"] = True
    if fault == "duplicate": row["candidates"][1]["itemQuantities"] = {"1036": 2}
    if fault == "short": row["candidates"][0]["features"].pop()
    if fault in ("nan", "inf", "null_feature", "bool_feature"):
        row["candidates"][0]["features"][0] = {"nan": float("nan"), "inf": float("inf"),
                                                "null_feature": None, "bool_feature": True}[fault]
    if fault == "wrong_positive": row["observedItems"] = {"1036": 3}
    path = tmp_path / "train.jsonl"
    path.write_text(json.dumps(row) + '\n' + json.dumps(row) + '\n')
    with pytest.raises(DatasetValidationError) as caught:
        load_dataset(path)
    assert caught.value.report["invalidGroups"] == 2
    assert caught.value.report["errorCounts"][reason] >= 1
    assert caught.value.report["excludedGroups"] == 0


def test_split_overlap_stops_training_and_writes_report(tmp_path, ranking_row):
    from component_ltr.dataset import load_splits
    for split in ("train", "validation", "test"):
        (tmp_path / f"{split}.jsonl").write_text(json.dumps(ranking_row) + '\n')
    (tmp_path / "manifest.json").write_text(json.dumps({"matches": {"KR_1": {"split": "train", "rankingGroups": 1}}}))
    report_path = tmp_path / "report.json"
    with pytest.raises(ValueError, match="검증 실패"):
        load_splits(tmp_path, report_path)
    report = json.loads(report_path.read_text())
    assert report["passed"] is False
    assert any(e["reason"] == "MATCH_OVERLAP" for e in report["splitErrors"])


def test_baselines_and_ndcg1_use_same_groups_and_tie_policy():
    import numpy as np
    from dfgg_ltr.dataset import Dataset
    from component_ltr.evaluate import evaluate_baselines
    features = np.zeros((3, len(FEATURE_NAMES)))
    features[:, 0] = [100, 200, 300]
    features[:, FEATURE_NAMES.index("consumed_owned_quantity")] = [0, 2, 2]
    dataset = Dataset(features, np.asarray([0, 1, 0]), np.asarray([3]),
                      ["KR_1:1:1:1"], FEATURE_NAMES, SCHEMA_FINGERPRINT)
    results = evaluate_baselines(dataset)
    assert results["MOST_MATERIAL_CONSUMED"]["hit@1"] == 1
    assert results["MOST_MATERIAL_CONSUMED"]["ndcg@1"] == 1
    assert results["CHEAPEST"]["mrr"] == 0.5
    assert results["COST_DESC"]["mrr"] == 0.5


def test_target_is_part_of_query_identity_and_feature_order_matches_java(tmp_path, ranking_row):
    import copy
    import re
    from pathlib import Path
    other = copy.deepcopy(ranking_row)
    other['targetItemId'] = 6333
    path = tmp_path / 'train.jsonl'
    path.write_text(json.dumps(ranking_row) + '\n' + json.dumps(other) + '\n')
    dataset = load_dataset(path)
    assert len(dataset.qids) == 2
    assert dataset.qids[0] != dataset.qids[1]
    java = (Path(__file__).parents[2] / 'dfgg/src/main/java/dfgg/application/recommend/component/ComponentCandidateFeatures.java').read_text()
    declaration = java.split('FEATURE_NAMES = List.of(', 1)[1].split(');', 1)[0]
    context = (Path(__file__).parents[2] / 'dfgg/src/main/java/dfgg/application/recommend/context/CompositionFeatures.java').read_text()
    composition = context.split('FEATURE_NAMES = List.of(', 1)[1].split(');', 1)[0]
    assert re.findall(r'"([a-z_]+)"', declaration + composition + java.split('PREPARATION_FEATURE_NAMES = List.of(', 1)[1].split(');', 1)[0]) == FEATURE_NAMES
    config = (Path(__file__).parents[2] / 'dfgg/src/main/java/dfgg/infrastructure/config/ComponentLtrConfiguration.java').read_text()
    assert SCHEMA_FINGERPRINT in config


def test_v4_data_is_rejected_even_if_feature_count_was_padded(tmp_path, ranking_row):
    ranking_row['schemaVersion'] = 'component-bundle-validation-v4'
    path = tmp_path / 'train.jsonl'
    path.write_text(json.dumps(ranking_row) + '\n')
    with pytest.raises(ValueError, match='SCHEMA_VERSION'):
        load_dataset(path)


def test_stratification_uses_metadata_without_adding_features(tmp_path, ranking_row):
    import numpy as np
    from component_ltr.evaluate import stratified_evaluation
    ranking_row.update(targetKind='TIER_TWO_BOOTS', completedCoreCount=2, goldLower=700, position='TOP')
    path = tmp_path / 'test.jsonl'
    path.write_text(json.dumps(ranking_row) + '\n')
    dataset = load_dataset(path)
    result = stratified_evaluation(dataset, np.asarray([1.0, 0.0]), path)
    assert result['target_kind']['TIER_TWO_BOOTS']['groups'] == 1
    assert result['completed_core_count']['2']['hit@1'] == 1
    assert result['gold_lower_range']['500-999']['hit@1'] == 1
    assert dataset.features.shape[1] == 59


def test_preparation_lookup_binding_is_required_and_train_only(tmp_path):
    import hashlib
    from component_ltr.dataset import load_splits
    matches = {}
    for split in ('train', 'validation', 'test'):
        match = 'KR_' + split
        matches[match] = {'split': split, 'rankingGroups': 1}
        row = dict(schemaVersion=SCHEMA_VERSION, featureNames=FEATURE_NAMES, status='READY',
                   targetSource='OBSERVED_FUTURE_TARGET_ITEM', matchId=match, participantId=1, targetItemId=3078,
                   dataVersion='16.18.1', startPurchaseOrder=1, endPurchaseOrder=1, observedItems={'3070': 1},
                   candidates=[dict(itemQuantities={'3070': 1}, label=1, features=[0] * 55 + [1, .25, math.log1p(50), .8],
                                    candidateSource='PREPARATION', preparationPatternCount=50, preparationFutureRecipeLinkRate=.8),
                               dict(itemQuantities={'1036': 1}, label=0, features=[0] * len(FEATURE_NAMES))])
        (tmp_path / (split + '.jsonl')).write_text(json.dumps(row) + '\n')
    manifest = {'matches': matches}
    (tmp_path / 'manifest.json').write_text(json.dumps(manifest))
    report = tmp_path / 'validation-report.json'
    with pytest.raises(ValueError):
        load_splits(tmp_path, report)
    assert json.loads(report.read_text())['splitErrors'][0]['reason'] == 'PREPARATION_LOOKUP_MISSING'
    lookup = dict(schemaVersion='component-preparation-v2', patch='16.18', miningSplit='train', trainMatchIds=['KR_train'])
    lookup_path = tmp_path / 'preparation-lookup.json'
    lookup_path.write_text(json.dumps(lookup))
    manifest['preparationLookupSha256'] = hashlib.sha256(lookup_path.read_bytes()).hexdigest()
    (tmp_path / 'manifest.json').write_text(json.dumps(manifest))
    assert len(load_splits(tmp_path, report)) == 3
    lookup['trainMatchIds'] += ['KR_test']
    lookup_path.write_text(json.dumps(lookup))
    manifest['preparationLookupSha256'] = hashlib.sha256(lookup_path.read_bytes()).hexdigest()
    (tmp_path / 'manifest.json').write_text(json.dumps(manifest))
    with pytest.raises(ValueError):
        load_splits(tmp_path, report)
    assert json.loads(report.read_text())['splitErrors'][0]['reason'] == 'PREPARATION_LOOKUP_CONTRACT'


@pytest.mark.parametrize("fault,reason", [("negative", "COMPOSITION_COUNT"),
                                         ("fraction", "COMPOSITION_COUNT"),
                                         ("candidate_specific", "COMPOSITION_QUERY_MISMATCH")])
def test_composition_is_nonnegative_integer_query_context(tmp_path, ranking_row, fault, reason):
    ranking_row['candidates'][0]['features'][46] = {'negative': -1, 'fraction': .5, 'candidate_specific': 1}[fault]
    path = tmp_path / 'train.jsonl'
    path.write_text(json.dumps(ranking_row) + '\n')
    with pytest.raises(ValueError, match=reason):
        load_dataset(path)


def test_v5_is_rejected_even_with_padded_55_features(tmp_path, ranking_row):
    ranking_row['schemaVersion'] = 'component-bundle-validation-v5'
    path = tmp_path / 'train.jsonl'
    path.write_text(json.dumps(ranking_row) + '\n')
    with pytest.raises(ValueError, match='SCHEMA_VERSION'):
        load_dataset(path)


def test_metadata_snapshot_hash_is_checked_before_training(tmp_path, ranking_row):
    from component_ltr.dataset import load_splits
    matches = {}
    for split in ('train', 'validation', 'test'):
        ranking_row['matchId'] = split
        matches[split] = {'split': split, 'rankingGroups': 1}
        (tmp_path / f'{split}.jsonl').write_text(json.dumps(ranking_row) + '\n')
    (tmp_path / 'manifest.json').write_text(json.dumps({'matches': matches, 'championMetadataSha256': 'a' * 64}))
    with pytest.raises(ValueError, match='검증 실패'):
        load_splits(tmp_path, tmp_path / 'validation-report.json')
    report = json.loads((tmp_path / 'validation-report.json').read_text())
    assert {'reason': 'CHAMPION_METADATA_HASH'} in report['splitErrors']


@pytest.mark.parametrize("values", [[1, .25, 3.9, .8], [0, .25, 0, 0], [0, 0, 0, .8]])
def test_target_path_cannot_carry_preparation_evidence(tmp_path, ranking_row, values):
    ranking_row['candidates'][0]['features'][55:] = values
    path = tmp_path / 'train.jsonl'
    path.write_text(json.dumps(ranking_row) + '\n')
    with pytest.raises(ValueError, match='PREPARATION_FEATURES'):
        load_dataset(path)
