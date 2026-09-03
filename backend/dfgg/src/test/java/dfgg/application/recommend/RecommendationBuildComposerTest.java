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

    private final Item boots = new Item(1L, "신발", List.of("Boots"));
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

        List<Item> composed = composer.compose(List.of(shortPopularBuild, longRareBuild), ChampionPosition.TOP);

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

        List<Item> composed = composer.compose(List.of(buildX, buildY), ChampionPosition.TOP);

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

        List<Item> composed = composer.compose(
                List.of(zLessObserved, zMoreObserved, wObserved),
                ChampionPosition.TOP
        );

        assertThat(composed).containsExactly(itemW);
    }

    @Test
    @DisplayName("매칭되는 통계가 없으면 빈 리스트를 반환한다")
    void compose_WhenNoMatchingStats_ReturnEmptyList() {
        List<Item> composed = composer.compose(List.of(), ChampionPosition.TOP);

        assertThat(composed).isEmpty();
    }

    @Test
    @DisplayName("BOTTOM은 일반 아이템의 표본이 더 많아도 신발을 관측된 구매 슬롯에 배치한다")
    void compose_WhenBottomBootIsOutrankedInItsSlot_KeepExactlyOneBootAtObservedSlot() {
        Item item0 = new Item(30L, "아이템0");
        Item item1 = new Item(31L, "아이템1");
        Item item2 = new Item(32L, "아이템2");
        Item item3 = new Item(33L, "아이템3");
        Item item4 = new Item(34L, "아이템4");
        Item item5 = new Item(35L, "아이템5");
        Item item6 = new Item(36L, "아이템6");

        ChampionBuildStats popularBootlessBuild = stats(
                "BOOTLESS",
                List.of(item0, item1, item2, item3, item4, item5, item6),
                100, 50, false, false, false, false, false
        );
        ChampionBuildStats observedBootBuild = stats(
                "WITH_BOOT",
                List.of(item0, item1, item2, boots, item4, item5, item6),
                10, 6, false, false, false, false, false
        );

        List<Item> composed = composer.compose(
                List.of(popularBootlessBuild, observedBootBuild),
                ChampionPosition.BOTTOM
        );

        assertThat(composed).containsExactly(item0, item1, item2, boots, item4, item5, item6);
        assertThat(composed.stream().filter(item -> item.hasTag("Boots"))).hasSize(1);
    }

    @Test
    @DisplayName("BOTTOM의 여러 신발 후보 중 표본이 가장 많은 신발 하나와 그 구매 슬롯을 사용한다")
    void compose_WhenBottomHasMultipleBootCandidates_PickOneWithItsObservedSlot() {
        Item otherBoots = new Item(40L, "다른 신발", List.of("Boots"));
        Item item0 = new Item(41L, "아이템0");
        Item item1 = new Item(42L, "아이템1");
        Item item2 = new Item(43L, "아이템2");
        Item item3 = new Item(44L, "아이템3");
        Item item4 = new Item(45L, "아이템4");
        Item item5 = new Item(46L, "아이템5");
        Item item6 = new Item(47L, "아이템6");

        ChampionBuildStats popularBootBuild = stats(
                "POPULAR_BOOT",
                List.of(item0, boots, item2, item3, item4, item5, item6),
                50, 30, false, false, false, false, false
        );
        ChampionBuildStats rareBootBuild = stats(
                "RARE_BOOT",
                List.of(item0, item1, item2, otherBoots, item4, item5, item6),
                10, 8, false, false, false, false, false
        );

        List<Item> composed = composer.compose(
                List.of(popularBootBuild, rareBootBuild),
                ChampionPosition.BOTTOM
        );

        assertThat(composed).containsExactly(item0, boots, item2, item3, item4, item5, item6);
        assertThat(composed).doesNotContain(otherBoots);
    }

    @Test
    @DisplayName("BOTTOM 통계에 신발 후보가 없으면 추천을 만들지 않는다")
    void compose_WhenBottomHasNoBootCandidate_ReturnEmptyList() {
        ChampionBuildStats bootlessBuild = stats(
                "BOOTLESS",
                List.of(
                        new Item(50L, "아이템0"),
                        new Item(51L, "아이템1"),
                        new Item(52L, "아이템2"),
                        new Item(53L, "아이템3"),
                        new Item(54L, "아이템4"),
                        new Item(55L, "아이템5"),
                        new Item(56L, "아이템6")
                ),
                100, 50, false, false, false, false, false
        );

        List<Item> composed = composer.compose(List.of(bootlessBuild), ChampionPosition.BOTTOM);

        assertThat(composed).isEmpty();
    }

    @Test
    @DisplayName("BOTTOM 통계로 일곱 슬롯을 채울 수 없으면 추천을 만들지 않는다")
    void compose_WhenBottomHasFewerThanSevenSlots_ReturnEmptyList() {
        ChampionBuildStats shortBuild = stats(
                "SHORT",
                List.of(boots, itemA2, itemA3, itemB4, itemB5, itemB6),
                100, 50, false, false, false, false, false
        );

        List<Item> composed = composer.compose(List.of(shortBuild), ChampionPosition.BOTTOM);

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
