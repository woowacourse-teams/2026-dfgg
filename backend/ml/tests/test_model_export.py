"""LightGBM 모델을 Java가 읽을 수 있는 평탄 형식으로 바꾸는 과정 검증.

`dump_model()`은 left_child/right_child가 중첩된 dict를 준다. Java 추론은 평탄 배열만 읽기로
했으므로(T12 계약) 여기서 변환한다. 이 변환이 틀리면 Python과 Java의 예측이 갈리는데,
그건 parity 테스트에서야 드러난다.
"""
import json

import lightgbm as lgb
import numpy as np
import pytest

from dfgg_ltr.model_export import flatten_model, to_export_dict


@pytest.fixture
def trained_booster():
    rng = np.random.default_rng(0)
    features = rng.random((200, 5))
    labels = rng.integers(0, 4, 200)
    dataset = lgb.Dataset(features, label=labels, group=[20] * 10,
                          feature_name=[f"f{i}" for i in range(5)])
    return lgb.train(
        {"objective": "lambdarank", "metric": "ndcg", "ndcg_eval_at": [5],
         "num_leaves": 7, "verbose": -1, "feature_pre_filter": False},
        dataset, num_boost_round=5,
    )


def test_flattens_every_tree(trained_booster):
    trees = flatten_model(trained_booster.dump_model())

    assert len(trees) == len(trained_booster.dump_model()["tree_info"])


def test_internal_node_arrays_have_matching_lengths(trained_booster):
    """분기 노드의 배열들은 길이가 같아야 한다 — Java가 같은 인덱스로 함께 읽는다."""
    for tree in flatten_model(trained_booster.dump_model()):
        node_count = len(tree["split_feature"])
        assert len(tree["threshold"]) == node_count
        assert len(tree["default_left"]) == node_count
        assert len(tree["left"]) == node_count
        assert len(tree["right"]) == node_count


def test_child_index_encoding_distinguishes_leaf_from_node(trained_booster):
    """자식 인덱스는 음수면 잎, 0 이상이면 분기 노드다 — 이 규약을 Java가 그대로 따른다."""
    for tree in flatten_model(trained_booster.dump_model()):
        for child in tree["left"] + tree["right"]:
            if child >= 0:
                assert child < len(tree["split_feature"])
            else:
                assert -child - 1 < len(tree["leaf_value"])


def test_single_leaf_tree_is_representable(trained_booster):
    """분기가 없는 트리(잎 하나)도 표현된다 — 학습이 일찍 수렴하면 실제로 나온다."""
    single_leaf = {"tree_info": [{"tree_structure": {"leaf_value": 0.42}}]}

    trees = flatten_model(single_leaf)

    assert trees[0]["split_feature"] == []
    assert trees[0]["leaf_value"] == [0.42]


def test_export_embeds_feature_names_and_fingerprint(trained_booster):
    """Java 로더가 스키마 어긋남을 잡으려면 모델이 이름과 지문을 들고 있어야 한다."""
    exported = to_export_dict(trained_booster, ["a", "b", "c", "d", "e"], "fp123")

    assert exported["feature_names"] == ["a", "b", "c", "d", "e"]
    assert exported["schema_fingerprint"] == "fp123"


def test_export_records_objective(trained_booster):
    exported = to_export_dict(trained_booster, ["a", "b", "c", "d", "e"], "fp123")

    assert exported["objective"].startswith("lambdarank")


def test_export_is_json_serializable(trained_booster):
    """NaN·Infinity가 섞이면 JSON이 깨진다 — Java가 표준 파서로 읽어야 한다."""
    exported = to_export_dict(trained_booster, ["a", "b", "c", "d", "e"], "fp123")

    text = json.dumps(exported, allow_nan=False)

    assert "NaN" not in text and "Infinity" not in text


def test_rejects_feature_name_count_mismatch(trained_booster):
    """모델이 쓰는 feature 수와 스키마 이름 수가 다르면 내보내지 않는다."""
    with pytest.raises(ValueError, match="feature"):
        to_export_dict(trained_booster, ["a", "b"], "fp123")


def test_rejects_categorical_split(trained_booster):
    """범주형 분기는 Java 추론이 다루지 않기로 했다 — 조용히 내보내면 예측이 갈린다."""
    categorical = {"tree_info": [{"tree_structure": {
        "split_feature": 0, "threshold": "1||2", "decision_type": "==",
        "default_left": True,
        "left_child": {"leaf_value": 0.1}, "right_child": {"leaf_value": 0.2},
    }}]}

    with pytest.raises(ValueError, match="categorical|범주형"):
        flatten_model(categorical)


class TestNodeCoverForShap:
    """TreeSHAP은 각 노드를 지난 학습 표본 수(cover)가 있어야 계산된다.

    분기 조건을 만족하지 않는 feature의 기여도를 "그 노드에서 양쪽으로 얼마나 갈렸는가"의
    비율로 나누기 때문이다. cover 없이는 SHAP을 아예 계산할 수 없다.
    """

    def _tree(self):
        import lightgbm as lgb
        import numpy as np
        from dfgg_ltr.model_export import flatten_model

        rng = np.random.default_rng(11)
        features = rng.random((200, 4))
        labels = rng.integers(0, 4, 200)
        dataset = lgb.Dataset(features, label=labels, group=[20] * 10,
                              feature_name=[f"f{i}" for i in range(4)])
        booster = lgb.train(
            {"objective": "lambdarank", "ndcg_eval_at": [5], "num_leaves": 7,
             "verbose": -1, "feature_pre_filter": False},
            dataset, num_boost_round=3)
        return flatten_model(booster.dump_model())[0], booster

    def test_internal_nodes_carry_their_cover(self):
        tree, _ = self._tree()

        assert len(tree["node_cover"]) == len(tree["split_feature"])
        assert all(cover > 0 for cover in tree["node_cover"])

    def test_leaves_carry_their_cover(self):
        tree, _ = self._tree()

        assert len(tree["leaf_cover"]) == len(tree["leaf_value"])
        assert all(cover > 0 for cover in tree["leaf_cover"])

    def test_a_nodes_cover_equals_the_sum_of_its_children(self):
        """이 관계가 깨지면 SHAP의 가중치가 틀어져 기여도 합이 예측과 어긋난다."""
        tree, _ = self._tree()

        def cover_of(index: int) -> float:
            return tree["node_cover"][index] if index >= 0 else tree["leaf_cover"][-index - 1]

        for index in range(len(tree["split_feature"])):
            assert cover_of(index) == pytest.approx(
                cover_of(tree["left"][index]) + cover_of(tree["right"][index]))

    def test_root_cover_equals_the_training_row_count(self):
        tree, _ = self._tree()

        assert tree["node_cover"][0] == pytest.approx(200)
