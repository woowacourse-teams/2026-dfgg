package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * feature별 SHAP 기여도를 묶음 단위로 접는다 — 서버 디버그 로그에서 "왜 이 순위인가"를 읽는 형태다.
 * <p>
 * 응답에서는 뺐다. SHAP은 "예측을 얼마나 밀었나"를 답할 뿐 "이유인가"를 답하지 못해 사용자에게 보일 근거가 아니었다.
 * 부호가 뒤집히는 경위를 추적하는 데는 여전히 쓸모가 있다.
 */
class GroupedContributionsTest {

    private double[] zeros() {
        return new double[FeatureName.values().length];
    }

    private void set(double[] values, FeatureName feature, double value) {
        values[feature.index()] = value;
    }

    @Test
    @DisplayName("같은 묶음의 feature 기여도를 더한다 — SHAP이 가산적이라 합쳐도 정확하다")
    void of_WhenGroupHasSeveralFeatures_SumsTheirContributions() {
        // given
        double[] values = zeros();
        set(values, FeatureName.BUILD_SCORE, 0.30);
        set(values, FeatureName.BUILD_RANK, 0.12);
        set(values, FeatureName.CHAMPION_BASE_RATE_ALL, -0.02);

        // when
        GroupedContributions grouped = GroupedContributions.of(new FeatureContributions(values, 0.5));

        // then
        assertThat(grouped.valueOf(ReasonGroup.BUILD)).isCloseTo(0.40, within(1e-9));
    }

    @Test
    @DisplayName("기여가 큰 묶음부터 낸다 — 가장 큰 이유가 맨 앞이다")
    void of_WhenContributionsDiffer_OrdersGroupsDescending() {
        // given
        double[] values = zeros();
        set(values, FeatureName.COUNTER_LIFT_MAX, 0.50);
        set(values, FeatureName.BUILD_SCORE, 0.10);
        set(values, FeatureName.ALLY_SCORE_MAX, -0.30);

        // when
        GroupedContributions grouped = GroupedContributions.of(new FeatureContributions(values, 0.0));

        // then
        assertThat(grouped.ordered().getFirst().group()).isEqualTo(ReasonGroup.COUNTER);
        assertThat(grouped.ordered().getLast().group()).isEqualTo(ReasonGroup.ALLY_SYNERGY);
    }

    @Test
    @DisplayName("기여가 0인 묶음도 빼지 않는다 — '패치는 영향이 없었다'도 정보다")
    void of_WhenGroupContributedNothing_StillKeepsIt() {
        // when
        GroupedContributions grouped = GroupedContributions.of(new FeatureContributions(zeros(), 0.0));

        // then
        assertThat(grouped.ordered()).hasSize(ReasonGroup.values().length);
    }

    @Test
    @DisplayName("묶음 기여도 합에 기준값을 더하면 모델 점수가 된다 — 접어도 항등식이 유지된다")
    void of_WhenFolded_PreservesTheAdditivityOfShap() {
        // given
        double[] values = zeros();
        set(values, FeatureName.BUILD_SCORE, 0.30);
        set(values, FeatureName.COUNTER_LIFT_MAX, 0.20);
        set(values, FeatureName.TIER_ORDINAL, -0.05);

        // when
        GroupedContributions grouped = GroupedContributions.of(new FeatureContributions(values, 1.5));

        // then
        double total = grouped.baseValue() + grouped.ordered().stream()
                .mapToDouble(GroupedContributions.GroupContribution::value).sum();
        assertThat(total).isCloseTo(1.5 + 0.45, within(1e-9));
    }

    @Test
    @DisplayName("기준값을 그대로 싣는다 — 없으면 기여도만으로 점수를 복원할 수 없다")
    void of_WhenFolded_CarriesTheBaseValue() {
        // when
        GroupedContributions grouped = GroupedContributions.of(new FeatureContributions(zeros(), 0.37));

        // then
        assertThat(grouped.baseValue()).isEqualTo(0.37);
    }

    @Test
    @DisplayName("기여가 같으면 선언 순서로 가른다 — 같은 입력의 로그가 매번 같다")
    void of_WhenContributionsTie_OrdersDeterministically() {
        // when
        GroupedContributions first = GroupedContributions.of(new FeatureContributions(zeros(), 0.0));
        GroupedContributions second = GroupedContributions.of(new FeatureContributions(zeros(), 0.0));

        // then
        assertThat(first.ordered()).isEqualTo(second.ordered());
    }
}
