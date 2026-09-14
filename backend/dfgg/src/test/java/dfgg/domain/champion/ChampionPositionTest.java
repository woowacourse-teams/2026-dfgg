package dfgg.domain.champion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChampionPositionTest {

    @ParameterizedTest(name = "{0}: {1}개 → {2}")
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
    @DisplayName("구매 아이템 수의 상한은 BOTTOM 7개, 그 외 6개다 — 상한은 포함이다")
    void allowsPurchasedItemCount_WhenCountIsAtOrOverTheLimit_AllowsOnlyUpToIt(
            ChampionPosition position, int purchasedItemCount, boolean expected) {
        // when
        boolean allowed = position.allowsPurchasedItemCount(purchasedItemCount);

        // then
        assertThat(allowed).isEqualTo(expected);
    }
}
