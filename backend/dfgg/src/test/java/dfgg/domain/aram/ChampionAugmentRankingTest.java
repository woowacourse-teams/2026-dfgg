package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChampionAugmentRankingTest {

    private static final String PATCH = "16.21";

    @Test
    @DisplayName("실버, 골드, 프리즘 순으로 묶는다")
    void of_OrdersByRarity() {
        // given
        List<Augment> catalog = List.of(augment(1, AugmentRarity.PRISMATIC), augment(2, AugmentRarity.SILVER),
                augment(3, AugmentRarity.GOLD));
        List<ChampionAugment> picks = List.of(pick(1, 30.0), pick(2, 10.0), pick(3, 20.0));

        // when
        ChampionAugmentRanking ranking = ChampionAugmentRanking.of(catalog, picks);

        // then
        assertThat(ranking.augments()).extracting(ranked -> ranked.augment().rarity())
                .containsExactly(AugmentRarity.SILVER, AugmentRarity.GOLD, AugmentRarity.PRISMATIC);
    }

    @Test
    @DisplayName("순위는 등급마다 1부터 픽률이 높은 순으로 매긴다")
    void of_RanksWithinRarityByPickRate() {
        // given
        List<Augment> catalog = List.of(augment(1, AugmentRarity.GOLD), augment(2, AugmentRarity.GOLD),
                augment(3, AugmentRarity.PRISMATIC));
        List<ChampionAugment> picks = List.of(pick(1, 5.0), pick(2, 16.8), pick(3, 1.0));

        // when
        ChampionAugmentRanking ranking = ChampionAugmentRanking.of(catalog, picks);

        // then
        assertThat(ranking.augments()).extracting(ranked -> ranked.augment().augmentId())
                .containsExactly(2, 1, 3);
        assertThat(ranking.augments()).extracting(RankedAugment::rank)
                .containsExactly(1, 2, 1);
    }

    @Test
    @DisplayName("픽률이 같으면 증강 ID가 작은 쪽이 앞선다")
    void of_BreaksTieByAugmentId() {
        // given
        List<Augment> catalog = List.of(augment(2, AugmentRarity.GOLD), augment(1, AugmentRarity.GOLD));
        List<ChampionAugment> picks = List.of(pick(2, 5.0), pick(1, 5.0));

        // when
        ChampionAugmentRanking ranking = ChampionAugmentRanking.of(catalog, picks);

        // then
        assertThat(ranking.augments()).extracting(ranked -> ranked.augment().augmentId())
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("표시할 픽률은 소수 첫째 자리로 반올림하고 순위는 원래 값으로 매긴다")
    void of_RoundsDisplayedPickRateButRanksByRawValue() {
        // given
        List<Augment> catalog = List.of(augment(1, AugmentRarity.GOLD), augment(2, AugmentRarity.GOLD));
        List<ChampionAugment> picks = List.of(pick(1, 16.76), pick(2, 16.79));

        // when
        ChampionAugmentRanking ranking = ChampionAugmentRanking.of(catalog, picks);

        // then
        assertThat(ranking.augments()).extracting(ranked -> ranked.augment().augmentId())
                .containsExactly(2, 1);
        assertThat(ranking.augments()).extracting(RankedAugment::displayedPickRate)
                .containsExactly(16.8, 16.8);
    }

    @Test
    @DisplayName("픽률이 없으면 빈 순위다")
    void of_WhenNoPicks_IsEmpty() {
        // when
        ChampionAugmentRanking ranking = ChampionAugmentRanking.of(List.of(augment(1, AugmentRarity.GOLD)), List.of());

        // then
        assertThat(ranking.augments()).isEmpty();
    }

    private Augment augment(int augmentId, AugmentRarity rarity) {
        return new Augment(PATCH, augmentId, Map.of("ko-KR", "증강" + augmentId), rarity);
    }

    private ChampionAugment pick(int augmentId, double pickRate) {
        return new ChampionAugment(PATCH, 22, augmentId, 100, pickRate);
    }
}
