package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.item.Item;
import dfgg.domain.recommendation.CoreBuildCluster;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoreBuildClusterServiceTest {

    private final CoreBuildClusterService service =
            new CoreBuildClusterService();

    private final Item itemA = new Item(1L, "아이템 A");
    private final Item itemB = new Item(2L, "아이템 B");
    private final Item itemC = new Item(3L, "아이템 C");
    private final Item itemD = new Item(4L, "아이템 D");

    @Test
    @DisplayName("같은 clusterKey의 통계는 하나의 군집으로 묶고 다른 키는 별도 군집으로 분리한다")
    void groupCoreBuild_GroupsByClusterKey() {
        // given
        ChampionBuildStats first = stats(itemA, itemB, itemC);
        ChampionBuildStats sameCluster = stats(itemB, itemA, itemC);
        ChampionBuildStats differentCluster = stats(itemA, itemB, itemD);

        // when
        List<CoreBuildCluster> clusters = service.groupCoreBuild(
                List.of(first, sameCluster, differentCluster)
        );

        // then
        assertThat(clusters).hasSize(2);

        assertThat(clusters.get(0).getClusterKey())
                .containsExactly(1L, 2L, 3L);
        assertThat(clusters.get(0).getObservedStats())
                .containsExactly(first, sameCluster);

        assertThat(clusters.get(1).getClusterKey())
                .containsExactly(1L, 2L, 4L);
        assertThat(clusters.get(1).getObservedStats())
                .containsExactly(differentCluster);
    }

    @Test
    @DisplayName("첫 3코어가 부족한 통계는 군집화에서 제외한다")
    void groupCoreBuild_ExcludesInsufficientBuilds() {
        // given
        ChampionBuildStats insufficient = stats(itemA, itemB);
        ChampionBuildStats valid = stats(itemA, itemB, itemC);

        // when
        List<CoreBuildCluster> clusters = service.groupCoreBuild(
                List.of(insufficient, valid)
        );

        // then
        assertThat(clusters).singleElement().satisfies(cluster -> {
            assertThat(cluster.getClusterKey())
                    .containsExactly(1L, 2L, 3L);
            assertThat(cluster.getObservedStats())
                    .containsExactly(valid);
        });
    }

    @Test
    @DisplayName("관측 통계가 없으면 빈 군집 목록을 반환한다")
    void groupCoreBuild_WhenStatsAreEmpty_ReturnsEmptyList() {
        assertThat(service.groupCoreBuild(List.of()))
                .isEmpty();
    }

    private ChampionBuildStats stats(Item... items) {
        ChampionBuildStats stats = mock(ChampionBuildStats.class);
        given(stats.getItems()).willReturn(List.of(items));
        return stats;
    }
}
