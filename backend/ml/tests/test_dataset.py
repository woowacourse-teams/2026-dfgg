"""학습 데이터 적재 검증.

JSONL을 LightGBM이 요구하는 형태(X, y, group)로 바꾸는 과정이 대상이다.
group을 잘못 만들면 lambdarank가 엉뚱한 묶음으로 순위를 배우는데, 그건 지표로 잘 드러나지 않는다.
"""
import json

import numpy as np
import pytest

from dfgg_ltr.dataset import baseline_view, load_dataset, prefix_view


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


def test_outcome_and_situation_columns_do_not_enter_features(tmp_path, schema):
    """승패·상황 메타 칸은 feature가 아니다 — 있어도 학습 입력이 그대로다."""
    data = tmp_path / "train.jsonl"
    with_meta = row("q1", 3, [1.0, 2.0, 3.0])
    with_meta.update({"win": 0, "purchase_minute": 12.5, "team_gold_diff": None, "own_total_gold": 8200})
    write_jsonl(data, [with_meta])

    dataset = load_dataset(data, schema, split="game", subset="train")

    assert dataset.features.shape == (1, 3)
    assert list(dataset.features[0]) == [1.0, 2.0, 3.0]
    assert list(dataset.labels) == [3]


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


# ─── 아이템 번호·구매 단계·기준 계약 ─────────────────────────────────────────

@pytest.fixture
def schema_with_baseline(tmp_path):
    """뒤에 feature가 더해진 스키마. 앞 2개가 기준 계약이다."""
    path = tmp_path / "feature_schema_with_baseline.json"
    path.write_text(json.dumps({
        "schema_fingerprint": "full789", "feature_count": 3,
        "baseline_schema_fingerprint": "base456", "baseline_feature_count": 2,
        "feature_names": ["f0", "f1", "f2"],
    }), encoding="utf-8")
    return path


def test_item_ids_follow_the_feature_rows(tmp_path, schema):
    """행마다 어느 아이템인지 싣는다 — 평가가 "이 아이템의 순위"를 물을 수 있어야 한다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        {**row("q1", 3, [1.0, 2.0, 3.0]), "item_id": 3089},
        {**row("q2", 3, [1.0, 2.0, 3.0]), "item_id": 6653},
        {**row("q1", 0, [1.0, 2.0, 3.0]), "item_id": 3031},
    ])

    dataset = load_dataset(data, schema, split="game", subset="train")

    # 행 순서는 qid로 묶은 순서다. 아이템 번호도 같은 순서를 따라야 feature 행과 맞는다.
    assert list(dataset.item_ids) == [3089, 3031, 6653]


def test_purchase_steps_are_kept_per_query(tmp_path, schema):
    """구매 단계는 query의 속성이다 — 몇 번째 Core를 고르는 스냅샷인지로 나눠 볼 때 쓴다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        {**row("q1", 3, [1.0, 2.0, 3.0]), "purchase_step": 0},
        {**row("q1", 0, [1.0, 2.0, 3.0]), "purchase_step": 0},
        {**row("q2", 3, [1.0, 2.0, 3.0]), "purchase_step": 2},
    ])

    dataset = load_dataset(data, schema, split="game", subset="train")

    assert list(dataset.purchase_steps) == [0, 2]
    assert len(dataset.purchase_steps) == len(dataset.group)


def test_baseline_contract_is_read_from_the_schema(tmp_path, schema_with_baseline):
    """기준 계약은 Java가 내보낸 값을 그대로 쓴다 — Python이 지문을 따로 계산하지 않는다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3, [1.0, 2.0, 3.0])])

    dataset = load_dataset(data, schema_with_baseline, split="game", subset="train")

    assert dataset.baseline_feature_count == 2
    assert dataset.baseline_schema_fingerprint == "base456"


def test_schema_without_baseline_contract_still_loads(tmp_path, schema):
    """기준 계약이 없는 옛 스키마 파일도 읽는다 — 기준 모델을 만들 때만 필요한 값이다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3, [1.0, 2.0, 3.0])])

    dataset = load_dataset(data, schema, split="game", subset="train")

    assert dataset.baseline_feature_count is None
    assert dataset.baseline_schema_fingerprint is None


def test_baseline_view_keeps_the_leading_columns_under_the_baseline_fingerprint(tmp_path, schema_with_baseline):
    """기준 모델은 앞쪽 feature만 본다. 칸 번호가 그대로라 서버의 기준 계약으로 읽을 수 있다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        {**row("q1", 3, [1.0, 2.0, 3.0]), "item_id": 3089},
        {**row("q1", 0, [4.0, None, 6.0]), "item_id": 3031},
    ])
    dataset = load_dataset(data, schema_with_baseline, split="game", subset="train")

    baseline = baseline_view(dataset)

    assert baseline.feature_names == ["f0", "f1"]
    assert baseline.schema_fingerprint == "base456"
    assert baseline.features.shape == (2, 2)
    assert baseline.features[0].tolist() == [1.0, 2.0]
    assert np.isnan(baseline.features[1, 1])
    # 라벨·묶음·아이템 번호는 그대로다. 같은 표본으로 두 모델을 비교해야 한다.
    assert list(baseline.labels) == list(dataset.labels)
    assert list(baseline.group) == list(dataset.group)
    assert list(baseline.item_ids) == [3089, 3031]


def test_prefix_view_keeps_the_first_columns_under_an_unservable_fingerprint(tmp_path, schema_with_baseline):
    """비교 기준을 앞 N칸으로 고를 때 쓴다. 칸 번호는 그대로지만 분석용이라 서버가 읽을 수 있는 지문을 붙이지 않는다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [
        {**row("q1", 3, [1.0, 2.0, 3.0]), "item_id": 3089},
        {**row("q1", 0, [4.0, None, 6.0]), "item_id": 3031},
    ])
    dataset = load_dataset(data, schema_with_baseline, split="game", subset="train")

    prefix = prefix_view(dataset, 2)

    assert prefix.feature_names == ["f0", "f1"]
    assert prefix.features.shape == (2, 2)
    assert prefix.features[0].tolist() == [1.0, 2.0]
    assert np.isnan(prefix.features[1, 1])
    assert prefix.schema_fingerprint not in (dataset.schema_fingerprint, dataset.baseline_schema_fingerprint)
    assert list(prefix.labels) == list(dataset.labels)
    assert list(prefix.group) == list(dataset.group)
    assert list(prefix.item_ids) == [3089, 3031]


@pytest.mark.parametrize("count", [0, 4])
def test_prefix_view_rejects_a_count_outside_the_schema(tmp_path, schema, count):
    """칸 수가 스키마를 벗어나면 조용히 잘리지 않고 멈춘다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3, [1.0, 2.0, 3.0])])
    dataset = load_dataset(data, schema, split="game", subset="train")

    with pytest.raises(ValueError, match="칸 수"):
        prefix_view(dataset, count)


def test_baseline_view_needs_the_baseline_contract(tmp_path, schema):
    """기준 계약이 없는 스키마로는 기준 모델을 만들 수 없다 — 지문을 지어내지 않고 멈춘다."""
    data = tmp_path / "train.jsonl"
    write_jsonl(data, [row("q1", 3, [1.0, 2.0, 3.0])])
    dataset = load_dataset(data, schema, split="game", subset="train")

    with pytest.raises(ValueError, match="기준 계약"):
        baseline_view(dataset)
