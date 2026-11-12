package dfgg.application.recommend.v3.feature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * feature 50개를 사용자가 읽을 수 있는 묶음으로 접는다.
 * <p>
 * SHAP은 가산적이라 그룹 기여도는 구성원 기여도의 합이고, 그 합에 기준값을 더하면 여전히 예측값이 된다.
 * 그래서 "빌드 통계 +0.42"처럼 말해도 수학적으로 정확하다.
 * {@code counter_lift_max +0.31} 같은 원시 값은 디버깅용이지 추천 이유가 아니다.
 */
class ReasonGroupTest {

    @Test
    @DisplayName("모든 feature가 어느 한 그룹에 속한다 — 빠지면 그 기여도가 응답에서 사라진다")
    void everyFeature_BelongsToExactlyOneGroup() {
        assertThat(Arrays.stream(FeatureName.values()).map(ReasonGroup::of))
                .doesNotContainNull()
                .hasSize(FeatureName.values().length);
    }

    @Test
    @DisplayName("빈 그룹은 두지 않는다 — 항상 0만 나오는 칸이 응답에 남는다")
    void everyGroup_HasAtLeastOneFeature() {
        EnumSet<ReasonGroup> used = EnumSet.noneOf(ReasonGroup.class);
        Arrays.stream(FeatureName.values()).map(ReasonGroup::of).forEach(used::add);

        assertThat(used).containsExactlyInAnyOrder(ReasonGroup.values());
    }

    @Test
    @DisplayName("generator가 찾았다는 표시는 그 generator의 그룹으로 간다")
    void sourceFlags_GoToTheirOwnGroup() {
        assertThat(ReasonGroup.of(FeatureName.SOURCE_COUNTER)).isEqualTo(ReasonGroup.COUNTER);
        assertThat(ReasonGroup.of(FeatureName.SOURCE_BUILD)).isEqualTo(ReasonGroup.BUILD);
    }

    @Test
    @DisplayName("아군 시너지 점수와 아군 조합 구성은 다른 그룹이다 — 이름만 비슷하고 뜻이 다르다")
    void allySynergyScores_AndTeamComposition_AreDifferentGroups() {
        assertThat(ReasonGroup.of(FeatureName.ALLY_SCORE_MAX)).isEqualTo(ReasonGroup.ALLY_SYNERGY);
        assertThat(ReasonGroup.of(FeatureName.ALLY_TANK_COUNT)).isEqualTo(ReasonGroup.TEAM_COMPOSITION);
    }

    @Test
    @DisplayName("최근 패치 기반 값은 패치 메타로 간다 — 전체 기간 구매율과 구분한다")
    void recentAndPatchFeatures_GoToPatchMeta() {
        assertThat(ReasonGroup.of(FeatureName.CHAMPION_BASE_RATE_ALL)).isEqualTo(ReasonGroup.BUILD);
        assertThat(ReasonGroup.of(FeatureName.CHAMPION_BASE_RATE_RECENT)).isEqualTo(ReasonGroup.PATCH_META);
        assertThat(ReasonGroup.of(FeatureName.ITEM_PICK_RATE_DELTA_3PATCH)).isEqualTo(ReasonGroup.PATCH_META);
    }
}
