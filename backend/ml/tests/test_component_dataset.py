"""최소 계약 검사: 후보 순서/그룹/정답/스키마 오류가 조용히 학습되지 않도록 한다."""
import json
import pytest
from component_ltr.dataset import load_dataset
from component_ltr.feature_schema import FEATURE_NAMES, SCHEMA_VERSION, SCHEMA_FINGERPRINT


def test_bundle_dataset_contract(tmp_path):
    row = {"schemaVersion": SCHEMA_VERSION, "featureNames": FEATURE_NAMES, "status": "READY",
           "targetSource": "OBSERVED_FUTURE_CORE", "matchId": "KR_1", "participantId": 1,
           "startPurchaseOrder": 1, "endPurchaseOrder": 2, "observedItems": {"1036": 2},
           "candidates": [{"itemQuantities": {"1036": 2}, "label": 1, "features": [700, 2, 1, 0, 2] + [0] * 36},
                          {"itemQuantities": {"1036": 1}, "label": 0, "features": [350, 1, 1, 0, 1] + [0] * 36}]}
    path = tmp_path / "train.jsonl"
    path.write_text(json.dumps(row) + '\n')
    dataset = load_dataset(path)
    assert dataset.group.tolist() == [2]
    assert dataset.labels.tolist() == [1, 0]
    assert dataset.features.shape == (2, 41)
    # Java 모델 로더의 계약 지문과 동일해야 한다.
    assert dataset.schema_fingerprint == SCHEMA_FINGERPRINT == (
        '9d8228aac189ef9ed2238458e922010d74fcda150865f938cbf57730cddc63ec'
    )
    row['candidates'][0]['label'] = 0
    path.write_text(json.dumps(row) + '\n')
    with pytest.raises(ValueError, match='정답'):
        load_dataset(path)


def test_validation_diagnostics_identifies_indistinguishable_candidates(tmp_path):
    """벡터가 같은 후보의 오답을 Feature 부족 사례로 따로 집계한다."""
    import numpy as np
    from dfgg_ltr.dataset import Dataset
    from component_ltr.analyze import diagnostics
    from component_ltr.evaluate import evaluate
    dataset = Dataset(np.zeros((2, 41)), np.asarray([0, 1]), np.asarray([2]),
                      ['KR_1:1:1:1'], FEATURE_NAMES, 'test-fingerprint')
    row = {'position': 'TOP', 'targetCoreId': 3071, 'observedItems': {'1036': 2},
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
            "targetSource": "OBSERVED_FUTURE_CORE", "matchId": "KR_1", "participantId": 1,
            "startPurchaseOrder": 1, "endPurchaseOrder": 2, "observedItems": {"1036": 2},
            "candidates": [{"itemQuantities": {"1036": 2}, "label": 1, "features": [700] + [0] * 40},
                           {"itemQuantities": {"1036": 1}, "label": 0, "features": [350] + [0] * 40}]}


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
    features = np.zeros((3, 41))
    features[:, 0] = [100, 200, 300]
    features[:, FEATURE_NAMES.index("consumed_owned_quantity")] = [0, 2, 2]
    dataset = Dataset(features, np.asarray([0, 1, 0]), np.asarray([3]),
                      ["KR_1:1:1:1"], FEATURE_NAMES, SCHEMA_FINGERPRINT)
    results = evaluate_baselines(dataset)
    assert results["MOST_MATERIAL_CONSUMED"]["hit@1"] == 1
    assert results["MOST_MATERIAL_CONSUMED"]["ndcg@1"] == 1
    assert results["CHEAPEST"]["mrr"] == 0.5
    assert results["COST_DESC"]["mrr"] == 0.5
