package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.domain.champion.ChampionTag;
import dfgg.domain.recommendation.BuildCandidate;
import dfgg.domain.recommendation.BuildDirection;
import dfgg.domain.recommendation.CoreBuildCluster;
import dfgg.domain.recommendation.SelectedBuildCandidate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildCandidateSelectServiceTest {

    private final BuildCandidateSelectService selector = new BuildCandidateSelectService();

    @Test
    @DisplayName("서로 다른 방향을 우선하여 최대 3개의 후보를 선택한다")
    void select_PrioritizesDistinctDirectionsAndLimitsToThree() {
        // given
        BuildCandidate firstTankCandidate = candidate(ChampionTag.TANK, "PHYSICAL_DAMAGE", 10.0, 1L);
        BuildCandidate secondTankCandidate = candidate(ChampionTag.TANK, "MAGIC_DAMAGE", 9.0, 2L);
        BuildCandidate mageCandidate = candidate(ChampionTag.MAGE, "BURST_DAMAGE", 8.0, 3L);
        BuildCandidate fighterCandidate = candidate(ChampionTag.FIGHTER, "ANTI_TANK", 7.0, 4L);

        // when
        List<SelectedBuildCandidate> selected = selector.select(List.of(
                firstTankCandidate,
                secondTankCandidate,
                mageCandidate,
                fighterCandidate
        ));

        // then
        assertThat(selected)
                .extracting(item -> item.candidate().direction().code())
                .containsExactly("PHYSICAL_DAMAGE", "MAGIC_DAMAGE", "BURST_DAMAGE");
        assertThat(selected)
                .extracting(SelectedBuildCandidate::recommended)
                .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("동일 clusterKey는 적합도가 높은 후보 하나만 남긴다")
    void select_WhenClusterKeyIsDuplicated_KeepsHigherSuitabilityCandidate() {
        // given
        BuildCandidate lowerCandidate = candidate(ChampionTag.TANK, "PHYSICAL_DAMAGE", 3.0, 10L);
        BuildCandidate higherCandidate = candidate(ChampionTag.MAGE, "BURST_DAMAGE", 8.0, 10L);
        BuildCandidate otherCandidate = candidate(ChampionTag.FIGHTER, "ANTI_TANK", 5.0, 11L);

        // when
        List<SelectedBuildCandidate> selected = selector.select(List.of(
                lowerCandidate,
                higherCandidate,
                otherCandidate
        ));

        // then
        assertThat(selected)
                .extracting(item -> item.candidate().direction().code())
                .containsExactly("BURST_DAMAGE", "ANTI_TANK");
        assertThat(selected.get(0).candidate().suitabilityScore()).isEqualTo(8.0);
    }

    @Test
    @DisplayName("서로 다른 방향 후보가 부족하면 같은 방향 후보로 남은 슬롯을 채운다")
    void select_WhenDistinctDirectionsAreInsufficient_FillsRemainingSlots() {
        // given
        BuildCandidate firstCandidate = candidate(ChampionTag.TANK, "PHYSICAL_DAMAGE", 10.0, 20L);
        BuildCandidate secondCandidate = candidate(ChampionTag.TANK, "PHYSICAL_DAMAGE", 9.0, 21L);
        BuildCandidate thirdCandidate = candidate(ChampionTag.TANK, "PHYSICAL_DAMAGE", 8.0, 22L);

        // when
        List<SelectedBuildCandidate> selected = selector.select(List.of(
                firstCandidate,
                secondCandidate,
                thirdCandidate
        ));

        // then
        assertThat(selected)
                .extracting(item -> item.candidate().cluster().getClusterKey())
                .containsExactly(List.of(20L), List.of(21L), List.of(22L));
    }

    @Test
    @DisplayName("추천 표시는 선택된 후보 중 적합도가 가장 높은 하나에만 부여한다")
    void select_MarksOnlyHighestSuitabilityCandidateAsRecommended() {
        // given
        BuildCandidate firstCandidate = candidate(ChampionTag.TANK, "PHYSICAL_DAMAGE", 5.0, 30L);
        BuildCandidate secondCandidate = candidate(ChampionTag.MAGE, "BURST_DAMAGE", 7.0, 31L);
        BuildCandidate thirdCandidate = candidate(ChampionTag.FIGHTER, "ANTI_TANK", 6.0, 32L);

        // when
        List<SelectedBuildCandidate> selected = selector.select(List.of(
                firstCandidate,
                secondCandidate,
                thirdCandidate
        ));

        // then
        assertThat(selected)
                .extracting(SelectedBuildCandidate::recommended)
                .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("후보가 없으면 빈 선택 결과를 반환한다")
    void select_WhenCandidatesAreEmpty_ReturnsEmptyList() {
        // given & when
        List<SelectedBuildCandidate> selected = selector.select(List.of());

        // then
        assertThat(selected).isEmpty();
    }

    @Test
    @DisplayName("후보 목록이 null이면 예외를 발생시킨다")
    void select_WhenCandidatesAreNull_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> selector.select(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("빌드 후보 목록은 null일 수 없습니다.");
    }

    private BuildCandidate candidate(
            ChampionTag championTag,
            String directionCode,
            double suitabilityScore,
            long clusterId
    ) {
        CoreBuildCluster cluster = mock(CoreBuildCluster.class);
        when(cluster.getClusterKey()).thenReturn(List.of(clusterId));
        return new BuildCandidate(
                new BuildDirection(championTag, directionCode),
                cluster,
                suitabilityScore
        );
    }
}
