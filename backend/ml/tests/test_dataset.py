"""학습 데이터 적재 검증.

JSONL을 LightGBM이 요구하는 형태(X, y, group)로 바꾸는 과정이 대상이다.
group을 잘못 만들면 lambdarank가 엉뚱한 묶음으로 순위를 배우는데, 그건 지표로 잘 드러나지 않는다.
"""
import json

import numpy as np
import pytest

from dfgg_ltr.dataset import load_dataset


def write_jsonl(path, rows):
    with path.open("w", encoding="utf-8") as out:
        for row in rows:
            out.write(json.dumps(row) + "\n")


def row(qid, label, features, split_game="train", split_patch="train"):
    return {
        "qid": qid, "label": label, "item_id": 1, "match_id": "M1",
        "patch": "16.16", "champion_id": 157, "position": "MID",
        "purchase_step": 0, "split_game": split_game, "split_patch": split_patch,
        "features": features,
    }


@pytest.fixture
def schema(tmp_path):
    path = tmp_path / "feature_schema.json"
    path.write_text(json.dumps({
        "schema_fingerprint": "abc123", "feature_count": 3,
        "feature_names": ["f0", "f1", "f2"],
    }), encoding="utf-8")
    return path


def test_group_sizes_follow_qid_boundaries(tmp_path, schema):
    """같은 qid의 행이 하나의 group이다 — lambdarank가 이 묶음 안에서 순위를 배운다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        row("q1", 3, [1.0, 2.0, 3.0]),
        row("q1", 0, [1.0, 2.0, 3.0]),
        row("q2", 3, [1.0, 2.0, 3.0]),
    ])

    dataset = load_dataset(data, schema, split="game", subset="train")

    assert list(dataset.group) == [2, 1]


def test_rows_of_same_query_stay_contiguous(tmp_path, schema):
    """qid가 섞여 있어도 group을 만들 수 있게 정렬한다 — 흩어져 있으면 group 경계가 깨진다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        row("q1", 3, [1.0, 2.0, 3.0]),
        row("q2", 3, [1.0, 2.0, 3.0]),
        row("q1", 0, [1.0, 2.0, 3.0]),
    ])

    dataset = load_dataset(data, schema, split="game", subset="train")

    assert sorted(dataset.group) == [1, 2]
    assert sum(dataset.group) == len(dataset.labels)


def test_null_features_become_nan(tmp_path, schema):
    """JSON의 null은 NaN이 된다 — '데이터 없음'과 '값이 0'의 구분이 학습까지 이어져야 한다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3, [1.0, None, 0.0])])

    dataset = load_dataset(data, schema, split="game", subset="train")

    assert np.isnan(dataset.features[0][1])
    assert dataset.features[0][2] == 0.0


def test_split_selects_only_requested_subset(tmp_path, schema):
    """game split과 patch split을 따로 고를 수 있다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        row("q1", 3, [1.0, 2.0, 3.0], split_game="train", split_patch="test"),
        row("q2", 3, [1.0, 2.0, 3.0], split_game="test", split_patch="train"),
    ])

    by_game = load_dataset(data, schema, split="game", subset="train")
    by_patch = load_dataset(data, schema, split="patch", subset="test")

    assert list(by_game.qids) == ["q1"]
    assert list(by_patch.qids) == ["q1"]


def test_feature_names_come_from_schema(tmp_path, schema):
    """feature 이름은 Java가 내보낸 스키마에서 온다 — Python이 지어내지 않는다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3, [1.0, 2.0, 3.0])])

    dataset = load_dataset(data, schema, split="game", subset="train")

    assert dataset.feature_names == ["f0", "f1", "f2"]
    assert dataset.schema_fingerprint == "abc123"


def test_rejects_row_whose_feature_count_differs_from_schema(tmp_path, schema):
    """벡터 길이가 스키마와 다르면 즉시 실패한다 — 조용히 어긋난 채 학습하면 안 된다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3, [1.0, 2.0])])

    with pytest.raises(ValueError, match="feature"):
        load_dataset(data, schema, split="game", subset="train")


def test_rejects_unknown_split(tmp_path, schema):
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3, [1.0, 2.0, 3.0])])

    with pytest.raises(ValueError):
        load_dataset(data, schema, split="season", subset="train")
