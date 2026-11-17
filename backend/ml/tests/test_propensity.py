"""행동 모델(propensity) 검증.

V는 1/p로 가중하므로 p가 정답 아이템을 가리키는지, 온도가 확률을 실제 선택률에 맞추는지가 대상이다.
"""
import json

import numpy as np
import pytest

from dfgg_ltr.dataset import Dataset, load_dataset, load_query_meta
from dfgg_ltr.propensity import (
    SITUATION_FEATURES,
    calibration,
    choice_probabilities,
    fit_temperature,
    ground_truth_probabilities,
    holdout_queries,
    select_queries,
    train_behavior_model,
    with_situation,
)


def write_jsonl(path, rows):
    with path.open("w", encoding="utf-8") as out:
        for row in rows:
            out.write(json.dumps(row) + "\n")


def row(qid, label, match_id="M1", split="train", **meta):
    base = {
        "qid": qid, "label": label, "item_id": 1, "match_id": match_id, "patch": "16.22",
        "champion_id": 157, "position": "MID", "purchase_step": 0,
        "split_game": split, "split_patch": split, "features": [1.0, 2.0],
    }
    base.update(meta)
    return base


@pytest.fixture
def schema(tmp_path):
    path = tmp_path / "feature_schema.json"
    path.write_text(json.dumps({
        "schema_fingerprint": "abc", "feature_count": 2, "feature_names": ["f0", "f1"],
    }), encoding="utf-8")
    return path


def sampled_queries(rng, queries, candidates, tau):
    """softmax(score / τ)로 정답을 뽑은 합성 데이터. 참 τ를 알고 있다."""
    scores = rng.normal(0, 1, queries * candidates)
    labels = np.zeros(queries * candidates, dtype=np.int32)
    group = np.full(queries, candidates)
    probabilities = choice_probabilities(scores, group, tau)
    for q in range(queries):
        start = q * candidates
        chosen = rng.choice(candidates, p=probabilities[start:start + candidates])
        labels[start + chosen] = 3
    return scores, labels, group


def test_choice_probabilities_sum_to_one_within_each_query():
    scores = np.array([1.0, 2.0, 3.0, 0.5, 0.5])
    group = np.array([3, 2])

    probabilities = choice_probabilities(scores, group, tau=1.0)

    assert probabilities[:3].sum() == pytest.approx(1.0)
    assert probabilities[3:].tolist() == pytest.approx([0.5, 0.5])


def test_higher_temperature_flattens_probabilities():
    scores = np.array([0.0, 2.0])
    group = np.array([2])

    sharp = choice_probabilities(scores, group, tau=0.5)
    flat = choice_probabilities(scores, group, tau=5.0)

    assert sharp[1] > flat[1] > 0.5


def test_ground_truth_probability_is_the_purchased_item_and_nan_without_one():
    scores = np.array([0.0, 0.0, 1.0, 1.0])
    labels = np.array([3, 0, 0, 2])
    group = np.array([2, 2])

    probabilities = ground_truth_probabilities(scores, labels, group, tau=1.0)

    assert probabilities[0] == pytest.approx(0.5)
    assert np.isnan(probabilities[1])


def test_fit_temperature_recovers_true_temperature():
    """참 τ로 선택을 뽑으면 맞춘 τ가 그 근처다."""
    rng = np.random.default_rng(0)
    scores, labels, group = sampled_queries(rng, queries=4000, candidates=8, tau=0.5)

    tau, likelihood = fit_temperature(scores, labels, group)

    assert tau == pytest.approx(0.5, rel=0.15)
    assert likelihood < 0


def test_calibration_matches_chosen_rate_when_probabilities_are_true():
    rng = np.random.default_rng(1)
    scores, labels, group = sampled_queries(rng, queries=4000, candidates=8, tau=0.5)

    table = calibration(scores, labels, group, tau=0.5)

    assert sum(b["count"] for b in table) == len(scores)
    for b in table:
        if b["count"] >= 500:
            assert b["chosen_rate"] == pytest.approx(b["mean_p"], abs=0.03)


def test_empty_calibration_bin_reports_none():
    table = calibration(np.array([0.0, 0.0]), np.array([3, 0]), np.array([2]), tau=1.0)

    assert table[0]["count"] == 0
    assert table[0]["mean_p"] is None
    assert table[5]["count"] == 2


def test_query_meta_follows_dataset_query_order_and_reads_missing_as_nan(tmp_path, schema):
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        row("q2", 3, win=1, purchase_minute=12.5, team_gold_diff=-300, own_total_gold=8000),
        row("q1", 3, win=0, purchase_minute=None, team_gold_diff=None, own_total_gold=None),
        row("q2", 0, win=1, purchase_minute=12.5, team_gold_diff=-300, own_total_gold=8000),
        row("q3", 3),
    ])

    dataset = load_dataset(data, schema, split="game", subset="train")
    meta = load_query_meta(data, split="game", subset="train")

    assert meta.qids == dataset.qids == ["q2", "q1", "q3"]
    assert meta.win[0] == 1 and meta.win[1] == 0 and np.isnan(meta.win[2])
    assert meta.team_gold_diff[0] == -300
    assert np.isnan(meta.purchase_minute[1]) and np.isnan(meta.purchase_minute[2])


def test_with_situation_repeats_query_values_on_every_row(tmp_path, schema):
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        row("q1", 3, purchase_minute=10.0, team_gold_diff=500),
        row("q1", 0, purchase_minute=10.0, team_gold_diff=500),
        row("q2", 3, purchase_minute=None, team_gold_diff=None),
    ])
    dataset = load_dataset(data, schema, split="game", subset="train")

    combined = with_situation(dataset, load_query_meta(data, split="game", subset="train"))

    assert combined.feature_names == ["f0", "f1", *SITUATION_FEATURES]
    assert combined.features[:2, 2:].tolist() == [[10.0, 500.0], [10.0, 500.0]]
    assert np.isnan(combined.features[2, 2:]).all()


def test_with_situation_rejects_meta_from_other_subset(tmp_path, schema):
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3), row("q2", 3, split="test")])
    dataset = load_dataset(data, schema, split="game", subset="train")

    with pytest.raises(ValueError, match="query 순서"):
        with_situation(dataset, load_query_meta(data, split="game", subset="test"))


def test_holdout_keeps_queries_of_one_match_together():
    match_ids = [f"KR_{i}" for i in range(200)] * 3

    mask = holdout_queries(match_ids)

    assert (mask[:200] == mask[200:400]).all() and (mask[:200] == mask[400:]).all()
    assert 0 < mask[:200].sum() < 60


def test_select_queries_keeps_rows_of_selected_queries():
    dataset = Dataset(
        features=np.arange(10, dtype=np.float64).reshape(5, 2), labels=np.array([3, 0, 3, 0, 0]),
        group=np.array([2, 3]), qids=["q1", "q2"], feature_names=["f0", "f1"], schema_fingerprint="abc")

    selected = select_queries(dataset, np.array([False, True]))

    assert selected.qids == ["q2"]
    assert selected.group.tolist() == [3]
    assert selected.labels.tolist() == [3, 0, 0]


def test_train_behavior_model_learns_from_train_split_only():
    """학습 split만으로 학습하고 온도를 맞춘다. 신호가 있는 feature면 정답 확률이 균등(1/후보 수)보다 높다."""
    rng = np.random.default_rng(2)
    queries, candidates = 1500, 6
    signal = rng.normal(0, 1, queries * candidates)
    group = np.full(queries, candidates)
    labels = np.zeros(queries * candidates, dtype=np.int32)
    for q in range(queries):
        labels[q * candidates + np.argmax(signal[q * candidates:(q + 1) * candidates]
                                          + rng.normal(0, 0.5, candidates))] = 3
    dataset = Dataset(
        features=np.column_stack([signal, rng.normal(0, 1, queries * candidates)]), labels=labels,
        group=group, qids=[f"q{q}" for q in range(queries)], feature_names=["signal", "noise"],
        schema_fingerprint="abc")

    booster, tau, likelihood = train_behavior_model(dataset, [f"KR_{q}" for q in range(queries)], rounds=50)
    probabilities = ground_truth_probabilities(booster.predict(dataset.features), labels, group, tau)

    assert tau > 0
    assert likelihood > np.log(1 / candidates)
    assert np.nanmean(probabilities) > 1 / candidates
