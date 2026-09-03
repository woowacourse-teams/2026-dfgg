package dfgg.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildCandidateTest {

    private final Item itemA = new Item(1L, "아이템 A");
    private final Item itemB = new Item(2L, "아이템 B");
    private final Item itemC = new Item(3L, "아이템 C");

    @Test
    @DisplayName("빌드 방향과 군집과 적합도를 후보에 보관한다")
    void createsBuildCandidate() {
        // given
        BuildDirection direction = new BuildDirection(ChampionTag.TANK, "PHYSICAL_DAMAGE");
        CoreBuildCluster cluster = createCluster();

        // when
        BuildCandidate candidate = new BuildCandidate(direction, cluster, 0.75);

        // then
        assertThat(candidate.direction()).isSameAs(direction);
        assertThat(candidate.cluster()).isSameAs(cluster);
        assertThat(candidate.suitabilityScore()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("적합도가 NaN이면 후보를 생성할 수 없다")
    void createsBuildCandidate_WhenScoreIsNaN_ThrowsException() {
        // given
        BuildDirection direction = new BuildDirection(ChampionTag.TANK, "PHYSICAL_DAMAGE");
        CoreBuildCluster cluster = createCluster();

        // when & then
        assertThatThrownBy(() -> new BuildCandidate(direction, cluster, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("빌드 적합도는 유한한 숫자여야 합니다.");
    }

    @Test
    @DisplayName("적합도가 무한대이면 후보를 생성할 수 없다")
    void createsBuildCandidate_WhenScoreIsInfinite_ThrowsException() {
        // given
        BuildDirection direction = new BuildDirection(ChampionTag.TANK, "PHYSICAL_DAMAGE");
        CoreBuildCluster cluster = createCluster();

        // when & then
        assertThatThrownBy(() -> new BuildCandidate(direction, cluster, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("빌드 적합도는 유한한 숫자여야 합니다.");
    }

    private CoreBuildCluster createCluster() {
        ChampionBuildStats stats = mock(ChampionBuildStats.class);
        given(stats.getItems()).willReturn(List.of(itemA, itemB, itemC));

        return CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(stats)
        );
    }
}
