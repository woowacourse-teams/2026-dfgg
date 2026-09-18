"""feature 일부를 빼고 학습해 그 그룹이 실제로 기여하는지 본다.

패치 민감 feature(`*recent*`, `*patch*`)를 빼도 지표가 그대로면, 패치를 반영한다는 설계가
말뿐이라는 뜻이다. 사용자가 "패치로 아이템 버프/너프가 나면 추천이 달라져야 한다"고 못박은
지점이라 여기서 확인한다.
"""
import numpy as np
import pytest

from dfgg_ltr.ablation import PATCH_AWARE, apply_feature_mask, select_feature_indices
from dfgg_ltr.dataset import Dataset

NAMES = ["build_score", "champion_base_rate_all", "champion_base_rate_recent",
         "item_pick_rate_current_patch", "tier_ordinal"]


def make_dataset() -> Dataset:
    return Dataset(
        features=np.arange(10, dtype=float).reshape(2, 5),
        labels=np.array([3, 0]),
        group=np.array([2]),
        qids=["q1"],
        feature_names=list(NAMES),
        schema_fingerprint="abc",
    )


class TestSelectFeatureIndices:
    def test_keeps_declaration_order(self):
        assert select_feature_indices(NAMES, drop=["tier_ordinal"]) == [0, 1, 2, 3]

    def test_patch_aware_group_drops_recent_and_patch_features(self):
        kept = select_feature_indices(NAMES, drop=PATCH_AWARE)

        assert [NAMES[i] for i in kept] == ["build_score", "champion_base_rate_all", "tier_ordinal"]

    def test_dropping_an_unknown_name_is_an_error_not_a_silent_no_op(self):
        """오타로 아무것도 빠지지 않은 채 '차이 없음'이라는 결론이 나오는 걸 막는다."""
        with pytest.raises(ValueError):
            select_feature_indices(NAMES, drop=["no_such_feature"])

    def test_dropping_everything_is_an_error(self):
        with pytest.raises(ValueError):
            select_feature_indices(NAMES, drop=NAMES)


class TestApplyFeatureMask:
    def test_slices_columns_and_names_together(self):
        masked = apply_feature_mask(make_dataset(), [0, 4])

        assert masked.feature_names == ["build_score", "tier_ordinal"]
        np.testing.assert_array_equal(masked.features, np.array([[0.0, 4.0], [5.0, 9.0]]))

    def test_leaves_labels_and_groups_untouched(self):
        original = make_dataset()

        masked = apply_feature_mask(original, [0, 4])

        np.testing.assert_array_equal(masked.labels, original.labels)
        np.testing.assert_array_equal(masked.group, original.group)

    def test_marks_the_fingerprint_as_ablated_so_it_cannot_be_served(self):
        """축소된 스키마의 모델을 실수로 서빙하면 Java 로더가 조용히 통과시키면 안 된다."""
        masked = apply_feature_mask(make_dataset(), [0, 4])

        assert masked.schema_fingerprint != "abc"
        assert "ablated" in masked.schema_fingerprint
