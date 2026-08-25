package dfgg.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CoreBuildClusterTest {

    private final Item itemA = new Item(1L, "아이템 A");
    private final Item itemB = new Item(2L, "아이템 B");
    private final Item itemC = new Item(3L, "아이템 C");
    private final Item itemD = new Item(4L, "아이템 D");

    @Test
    @DisplayName("같은 clusterKey의 원본 통계를 군집 안에 보존한다")
    void from_PreservesObservedStatsWithSameClusterKey() {
        // given
        ChampionBuildStats first = stats(itemA, itemB, itemC);
        ChampionBuildStats second = stats(itemB, itemA, itemC);

        CoreBuildCluster cluster = CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(first, second)
        );
        // when & then
        assertThat(cluster.getClusterKey())
                .containsExactly(1L, 2L, 3L);
        assertThat(cluster.getObservedStats())
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("서로 다른 clusterKey의 통계를 하나의 군집으로 만들 수 없다.")
    void from_WithDifferentClusterKey_ThrowsException() {
        // given
        ChampionBuildStats first = stats(itemA, itemB, itemC);
        ChampionBuildStats different = stats(itemA, itemB, itemD);

        // when & then
        assertThatThrownBy(() -> CoreBuildCluster.from(List.of(1L, 2L, 3L), List.of(first, different)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("모든 관측 빌드는 동일한 clusterKey를 가져야 합니다.");
    }

    @Test
    @DisplayName("첫 3코어가 부족한 통계는 군집으로 만들 수 없다")
    void from_WithInsufficientCoreItems_ThrowsException() {
        // given
        ChampionBuildStats insufficient = stats(itemA, itemB);

        // when & then
        assertThatThrownBy(() -> CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(insufficient)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관측 빌드에는 신발을 제외한 코어 아이템 3개가 필요합니다.");
    }


    private ChampionBuildStats stats(Item... items) {
        ChampionBuildStats stats = mock(ChampionBuildStats.class);
        given(stats.getItems()).willReturn(List.of(items));
        return stats;
    }

}
