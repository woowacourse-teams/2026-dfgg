package dfgg.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import dfgg.application.recommend.v3.ranker.FeatureContributions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 추천 이유 = 어느 묶음이 이 아이템의 점수를 얼마나 올리고 내렸는가.
 * <p>
 * generator가 준 점수를 그대로 옮기던 이전 방식을 대체한다.
 */
class RecommendationReasonsTest {

    private double[] zeros() {
        return new double[FeatureName.values().length];
    }

    private void set(double[] values, FeatureName feature, double value) {
        values[feature.index()] = value;
    }

    @Test
    @DisplayName("같은 그룹의 feature 기여도를 더한다 — SHAP이 가산적이라 합쳐도 정확하다")
    void of_SumsContributionsWithinAGroup() {
        double[] values = zeros();
        set(values, FeatureName.BUILD_SCORE, 0.30);
        set(values, FeatureName.BUILD_RANK, 0.12);
        set(values, FeatureName.CHAMPION_BASE_RATE_ALL, -0.02);

        RecommendationReasons reasons = RecommendationReasons.of(new FeatureContributions(values, 0.5));

        assertThat(valueOf(reasons, ReasonGroup.BUILD)).isCloseTo(0.40, within(1e-9));
    }

    @Test
    @DisplayName("기여가 큰 그룹부터 낸다 — 가장 큰 이유가 맨 앞이다")
    void of_OrdersGroupsByContributionDescending() {
        double[] values = zeros();
        set(values, FeatureName.COUNTER_LIFT_MAX, 0.50);
        set(values, FeatureName.BUILD_SCORE, 0.10);
        set(values, FeatureName.ALLY_SCORE_MAX, -0.30);

        RecommendationReasons reasons = RecommendationReasons.of(new FeatureContributions(values, 0.0));

        assertThat(reasons.contributions().get(0).group()).isEqualTo(ReasonGroup.COUNTER.name());
        assertThat(reasons.contributions().getLast().group()).isEqualTo(ReasonGroup.ALLY_SYNERGY.name());
    }

    @Test
    @DisplayName("기여가 0인 그룹도 빼지 않는다 — '패치는 영향이 없었다'도 이유다")
    void of_KeepsGroupsThatContributedNothing() {
        RecommendationReasons reasons = RecommendationReasons.of(new FeatureContributions(zeros(), 0.0));

        assertThat(reasons.contributions()).hasSize(ReasonGroup.values().length);
    }

    @Test
    @DisplayName("그룹 기여도 합에 기준값을 더하면 모델 점수가 된다 — 접어도 항등식이 유지된다")
    void of_PreservesTheAdditivityOfShap() {
        double[] values = zeros();
        set(values, FeatureName.BUILD_SCORE, 0.30);
        set(values, FeatureName.COUNTER_LIFT_MAX, 0.20);
        set(values, FeatureName.TIER_ORDINAL, -0.05);

        RecommendationReasons reasons = RecommendationReasons.of(new FeatureContributions(values, 1.5));

        double total = reasons.baseValue()
                + reasons.contributions().stream().mapToDouble(GroupContribution::value).sum();
        assertThat(total).isCloseTo(1.5 + 0.45, within(1e-9));
    }

    @Test
    @DisplayName("기준값을 그대로 싣는다 — 없으면 기여도만으로 점수를 복원할 수 없다")
    void of_CarriesTheBaseValue() {
        RecommendationReasons reasons = RecommendationReasons.of(new FeatureContributions(zeros(), 0.37));

        assertThat(reasons.baseValue()).isEqualTo(0.37);
    }

    @Test
    @DisplayName("그룹 이름이 같은 값이면 순서가 매번 같다 — 흔들리면 UI가 흔들린다")
    void of_BreaksTiesDeterministically() {
        RecommendationReasons first = RecommendationReasons.of(new FeatureContributions(zeros(), 0.0));
        RecommendationReasons second = RecommendationReasons.of(new FeatureContributions(zeros(), 0.0));

        assertThat(first.contributions()).isEqualTo(second.contributions());
    }

    private double valueOf(RecommendationReasons reasons, ReasonGroup group) {
        return reasons.contributions().stream()
                .filter(contribution -> contribution.group().equals(group.name()))
                .findFirst().orElseThrow()
                .value();
    }
}
