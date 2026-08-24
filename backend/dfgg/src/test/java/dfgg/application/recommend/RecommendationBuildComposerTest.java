package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendationBuildComposerTest {

    private final RecommendationBuildComposer composer = new RecommendationBuildComposer();

    private final Champion champion = new Champion(1L, "riot-key", "챔피언", List.of(ChampionTag.FIGHTER));

    private final Item boots = new Item(1L, "신발");
    private final Item itemA2 = new Item(2L, "아이템A2");
    private final Item itemA3 = new Item(3L, "아이템A3");
    private final Item itemB4 = new Item(4L, "아이템B4");
    private final Item itemB5 = new Item(5L, "아이템B5");
    private final Item itemB6 = new Item(6L, "아이템B6");

    @Test
    @DisplayName("표본이 많은 짧은 빌드와 표본이 적은 긴 빌드를 슬롯별로 병합해 최대 6개까지 채운다")
    void compose_WhenShortPopularBuildAndLongRareBuildExist_MergeItemsBySlotUpToSix() {
        ChampionBuildStats shortPopularBuild = stats(
                "A", List.of(boots, itemA2, itemA3), 50, 30, false, false, false, false, false
        );
        ChampionBuildStats longRareBuild = stats(
                "B", List.of(boots, itemA2, itemA3, itemB4, itemB5, itemB6), 3, 2,
                false, false, false, false, false
        );

        List<Item> composed = composer.compose(List.of(shortPopularBuild, longRareBuild));

        assertThat(composed).containsExactly(boots, itemA2, itemA3, itemB4, itemB5, itemB6);
    }

    @Test
    @DisplayName("같은 아이템이 여러 슬롯의 후보로 나오면 먼저 채택된 슬롯에서만 사용하고 이후 슬롯에서는 제외한다")
    void compose_WhenSameItemCandidatesMultipleSlots_KeepFirstSlotAndExcludeFromLaterSlots() {
        Item itemP = new Item(10L, "P");
        Item itemQ = new Item(11L, "Q");
        Item itemR = new Item(12L, "R");

        ChampionBuildStats buildX = stats(
                "X", List.of(itemP, itemQ), 20, 10, false, false, false, false, false
        );
        ChampionBuildStats buildY = stats(
                "Y", List.of(itemR, itemP), 15, 5, false, false, false, false, false
        );

        List<Item> composed = composer.compose(List.of(buildX, buildY));

        assertThat(composed).containsExactly(itemP, itemQ);
        assertThat(composed).doesNotContain(itemR);
    }

    @Test
    @DisplayName("같은 buildKey의 여러 통계 중 표본이 가장 많은 행만 반영하고 이중 집계하지 않는다")
    void compose_WhenBuildKeyHasMultipleRows_UseOnlyMostObservedRow() {
        Item itemZ = new Item(20L, "Z");
        Item itemW = new Item(21L, "W");

        ChampionBuildStats zLessObserved = stats(
                "Z", List.of(itemZ), 40, 20, true, true, true, true, true
        );
        ChampionBuildStats zMoreObserved = stats(
                "Z", List.of(itemZ), 50, 25, true, true, true, true, true
        );
        ChampionBuildStats wObserved = stats(
                "W", List.of(itemW), 80, 40, true, true, true, true, true
        );

        List<Item> composed = composer.compose(List.of(zLessObserved, zMoreObserved, wObserved));

        assertThat(composed).containsExactly(itemW);
    }

    @Test
    @DisplayName("매칭되는 통계가 없으면 빈 리스트를 반환한다")
    void compose_WhenNoMatchingStats_ReturnEmptyList() {
        List<Item> composed = composer.compose(List.of());

        assertThat(composed).isEmpty();
    }

    private ChampionBuildStats stats(
            String buildKey,
            List<Item> items,
            int gameCount,
            int winCount,
            Boolean enemyTankHeavy,
            Boolean enemyApHeavy,
            Boolean enemyAssassinHeavy,
            Boolean allyHasMarksman,
            Boolean allyTankHeavy
    ) {
        return new ChampionBuildStats(
                "16.15",
                420,
                champion,
                ChampionPosition.TOP,
                enemyTankHeavy,
                enemyApHeavy,
                enemyAssassinHeavy,
                allyHasMarksman,
                allyTankHeavy,
                "PLATINUM",
                buildKey,
                items,
                winCount,
                gameCount
        );
    }
}
