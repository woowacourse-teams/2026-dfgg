"""합성 후보로 "이 근거만 강하면 몇 위인가"를 직접 물어본다.

실데이터 관측은 여러 근거가 뒤섞여 있어 인과를 분리하기 어렵다. 다른 값은 전부 중앙값으로
고정하고 한 묶음만 올리거나 내려서, 그 묶음 하나가 순위를 얼마나 움직이는지 본다.
"""
import numpy as np
import pytest

from dfgg_ltr.scenario import build_scenarios, percentile_profile

GROUPS = {"BUILD": [0, 1], "COUNTER": [2, 3]}


class TestPercentileProfile:
    def test_gives_low_median_high_for_each_column(self):
        features = np.arange(100, dtype=float).reshape(50, 2)

        profile = percentile_profile(features, low=10, high=90)

        assert profile["median"].shape == (2,)
        assert np.all(profile["low"] < profile["median"])
        assert np.all(profile["median"] < profile["high"])

    def test_ignores_missing_values_instead_of_propagating_nan(self):
        """결측이 섞이면 백분위가 NaN이 되어 시나리오 전체가 무의미해진다."""
        features = np.array([[1.0, np.nan], [2.0, 5.0], [3.0, 7.0], [4.0, 9.0]])

        profile = percentile_profile(features, low=10, high=90)

        assert not np.isnan(profile["median"]).any()

    def test_a_column_that_is_entirely_missing_stays_missing(self):
        """값이 하나도 없으면 중앙값을 지어낼 수 없다. 결측으로 두는 편이 정직하다."""
        features = np.array([[1.0, np.nan], [2.0, np.nan]])

        profile = percentile_profile(features, low=10, high=90)

        assert np.isnan(profile["median"][1])


class TestBuildScenarios:
    def _profile(self):
        return {
            "low": np.array([0.0, 0.0, 0.0, 0.0]),
            "median": np.array([5.0, 5.0, 5.0, 5.0]),
            "high": np.array([9.0, 9.0, 9.0, 9.0]),
        }

    def test_boosted_group_takes_the_high_value(self):
        rows, names = build_scenarios(self._profile(), GROUPS,
                                      [("counter만 강함", ["COUNTER"], ["BUILD"])])

        assert rows[0][2] == 9.0 and rows[0][3] == 9.0

    def test_suppressed_group_takes_the_low_value(self):
        rows, names = build_scenarios(self._profile(), GROUPS,
                                      [("counter만 강함", ["COUNTER"], ["BUILD"])])

        assert rows[0][0] == 0.0 and rows[0][1] == 0.0

    def test_untouched_columns_stay_at_the_median(self):
        profile = self._profile()
        groups = {"BUILD": [0], "COUNTER": [2]}

        rows, _ = build_scenarios(profile, groups, [("t", ["COUNTER"], ["BUILD"])])

        assert rows[0][1] == 5.0 and rows[0][3] == 5.0

    def test_returns_one_row_per_scenario_with_its_name(self):
        scenarios = [("a", ["COUNTER"], []), ("b", ["BUILD"], [])]

        rows, names = build_scenarios(self._profile(), GROUPS, scenarios)

        assert rows.shape[0] == 2
        assert names == ["a", "b"]

    def test_rejects_a_scenario_naming_an_unknown_group(self):
        with pytest.raises(ValueError):
            build_scenarios(self._profile(), GROUPS, [("t", ["NO_SUCH"], [])])
