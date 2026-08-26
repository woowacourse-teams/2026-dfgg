package dfgg.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CoreBuildClusterTest {

    private final Item itemA = new Item(1L, "아이템 A");
    private final Item itemB = new Item(2L, "아이템 B");
    private final Item itemC = new Item(3L, "아이템 C");
    private final Item itemD = new Item(4L, "아이템 D");
    private final Item itemE = new Item(5L, "아이템 E");
    private final Item itemF = new Item(6L, "아이템 F");
    private final Item boots = new Item(10L, "신발", List.of("Boots"));

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

    @Test
    @DisplayName("군집의 gameCount와 winCount를 합산한다")
    void from_SumsClusterStats() {
        // given
        ChampionBuildStats first = stats(6, 3, itemA, itemB, itemC);
        ChampionBuildStats second = stats(4, 2, itemB, itemA, itemC);

        // when
        CoreBuildCluster cluster = CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(first, second)
        );

        // then
        assertThat(cluster.getGameCount()).isEqualTo(10);
        assertThat(cluster.getWinCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("같은 구매 순서의 통계를 누적해 대표 순서를 선정한다")
    void from_SelectsMostObservedPurchaseOrder() {
        // given
        ChampionBuildStats first = stats(6, 3, itemA, itemB, itemC);
        ChampionBuildStats sameOrder = stats(5, 2, itemA, itemB, itemC, itemD);
        ChampionBuildStats otherOrder = stats(10, 7, itemB, itemA, itemC);

        // when
        CoreBuildCluster cluster = CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(first, sameOrder, otherOrder)
        );

        // then
        assertThat(cluster.getRepresentativeSequence().getOrderedItems())
                .containsExactly(itemA, itemB, itemC);
    }

    @Test
    @DisplayName("대표 순서의 통계가 동률이면 먼저 관측된 순서를 유지한다")
    void from_WhenRepresentativeOrderIsTied_KeepsFirstObservedOrder() {
        // given
        ChampionBuildStats first = stats(10, 5, itemA, itemB, itemC);
        ChampionBuildStats second = stats(10, 5, itemB, itemA, itemC);

        // when
        CoreBuildCluster cluster = CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(first, second)
        );

        // then
        assertThat(cluster.getRepresentativeSequence().getOrderedItems())
                .containsExactly(itemA, itemB, itemC);
    }

    @Test
    @DisplayName("대표 순서와 일치하는 실제 완성 빌드를 선택한다")
    void findRepresentativeBuild_SelectsObservedCompleteBuild() {
        // given
        ChampionBuildStats partial = stats(100, 50, itemA, itemB, itemC);
        ChampionBuildStats complete = stats(
                5,
                2,
                itemA,
                boots,
                itemB,
                itemC
        );

        // when
        CoreBuildCluster cluster = CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(partial, complete)
        );

        // then
        assertThat(cluster.findRepresentativeBuild(4))
                .hasValue(complete);
        assertThat(cluster.findOrComposeRepresentativeBuild(4))
                .hasValue(List.of(itemA, boots, itemB, itemC));
        assertThat(cluster.findRepresentativeBuild(6))
                .isEmpty();
    }

    @Test
    @DisplayName("완성 빌드가 없으면 같은 군집의 후반 아이템 통계로 6개를 채운다")
    void findOrComposeRepresentativeBuild_ComposesMissingLateItems() {
        // given
        ChampionBuildStats firstPartial = stats(
                10,
                5,
                itemA,
                boots,
                itemB,
                itemC,
                itemD
        );
        ChampionBuildStats secondPartial = stats(
                8,
                4,
                itemA,
                itemB,
                itemC,
                itemD,
                itemE
        );
        ChampionBuildStats otherLateItem = stats(
                3,
                2,
                itemA,
                itemB,
                itemC,
                itemF
        );
        CoreBuildCluster cluster = CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(firstPartial, secondPartial, otherLateItem)
        );

        // when
        Optional<List<Item>> completed =
                cluster.findOrComposeRepresentativeBuild(6);

        // then
        assertThat(completed)
                .hasValue(List.of(
                        itemA,
                        boots,
                        itemB,
                        itemC,
                        itemD,
                        itemE
                ));
    }

    @Test
    @DisplayName("후반 아이템 슬롯을 모두 채울 수 없으면 완성 빌드를 만들지 않는다")
    void findOrComposeRepresentativeBuild_WhenLateItemsAreInsufficient_ReturnsEmpty() {
        // given
        ChampionBuildStats partial = stats(
                10,
                5,
                itemA,
                boots,
                itemB,
                itemC,
                itemD
        );
        CoreBuildCluster cluster = CoreBuildCluster.from(
                List.of(1L, 2L, 3L),
                List.of(partial)
        );

        // when
        Optional<List<Item>> completed =
                cluster.findOrComposeRepresentativeBuild(6);

        // then
        assertThat(completed).isEmpty();
    }

    private ChampionBuildStats stats(Item... items) {
        ChampionBuildStats stats = mock(ChampionBuildStats.class);
        given(stats.getItems()).willReturn(List.of(items));
        return stats;
    }

    private ChampionBuildStats stats(
            int gameCount,
            int winCount,
            Item... items
    ) {
        ChampionBuildStats stats = stats(items);
        given(stats.getGameCount()).willReturn(gameCount);
        given(stats.getWinCount()).willReturn(winCount);
        return stats;
    }

}
