package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChampionPositionNormalizerTest {

    private final ChampionPositionNormalizer normalizer = new ChampionPositionNormalizer();

    @Test
    @DisplayName("Riot API의 MIDDLE은 MID로 정규화한다")
    void normalize_WhenPositionIsMiddle_ReturnsMid() {
        // given & when
        Optional<ChampionPosition> position = normalizer.normalize("MIDDLE");

        // then
        assertThat(position).contains(ChampionPosition.MID);
    }

    @Test
    @DisplayName("Riot API의 UTILITY는 SUPPORT로 정규화한다")
    void normalize_WhenPositionIsUtility_ReturnsSupport() {
        // given & when
        Optional<ChampionPosition> position = normalizer.normalize("UTILITY");

        // then
        assertThat(position).contains(ChampionPosition.SUPPORT);
    }

    @Test
    @DisplayName("ChampionPosition과 이름이 같은 값은 그대로 정규화한다")
    void normalize_WhenPositionMatchesEnumName_ReturnsSameEnum() {
        // given & when
        Optional<ChampionPosition> position = normalizer.normalize("top");

        // then
        assertThat(position).contains(ChampionPosition.TOP);
    }

    @Test
    @DisplayName("알 수 없는 포지션 문자열은 빈 값을 반환한다")
    void normalize_WhenPositionIsUnknown_ReturnsEmpty() {
        // given & when
        Optional<ChampionPosition> position = normalizer.normalize("INVALID");

        // then
        assertThat(position).isEmpty();
    }

    @Test
    @DisplayName("null 또는 공백 포지션은 빈 값을 반환한다")
    void normalize_WhenPositionIsBlankOrNull_ReturnsEmpty() {
        // given & when & then
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.normalize("  ")).isEmpty();
    }
}
