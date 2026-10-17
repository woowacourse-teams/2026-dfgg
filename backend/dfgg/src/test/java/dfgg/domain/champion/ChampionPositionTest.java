package dfgg.domain.champion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChampionPositionTest {

    @ParameterizedTest(name = "{0}: {1}개 → 받는가 {2}")
    @CsvSource({
            "BOTTOM, 7, true",
            "BOTTOM, 8, false",
            "TOP, 6, true",
            "TOP, 7, false",
            "JUNGLE, 6, true",
            "JUNGLE, 7, false",
            "MID, 6, true",
            "MID, 7, false",
            "SUPPORT, 6, true",
            "SUPPORT, 7, false",
    })
    @DisplayName("구매 아이템은 상한(BOTTOM 7개, 그 외 6개)까지 받는다 — 넘으면 잘못된 요청이다")
    void allowsPurchasedItemCount_WhenCountIsAtOrOverTheLimit_AllowsOnlyUpToIt(
            ChampionPosition position, int purchasedItemCount, boolean expected) {
        // when
        boolean allowed = position.allowsPurchasedItemCount(purchasedItemCount);

        // then
        assertThat(allowed).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}: {1}개 → 풀템인가 {2}")
    @CsvSource({
            "BOTTOM, 6, false",
            "BOTTOM, 7, true",
            "TOP, 5, false",
            "TOP, 6, true",
            "JUNGLE, 5, false",
            "JUNGLE, 6, true",
            "MID, 5, false",
            "MID, 6, true",
            "SUPPORT, 5, false",
            "SUPPORT, 6, true",
    })
    @DisplayName("상한에 닿으면 풀템이다 — 더 살 자리가 없다")
    void isFullBuild_WhenCountReachesTheLimit_IsTrue(
            ChampionPosition position, int purchasedItemCount, boolean expected) {
        // when
        boolean fullBuild = position.isFullBuild(purchasedItemCount);

        // then
        assertThat(fullBuild).isEqualTo(expected);
    }
}
