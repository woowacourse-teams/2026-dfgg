package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
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

    @Test
    @DisplayName("MID는 Riot 원시값 MIDDLE과 MID를 모두 조회 대상으로 되돌린다")
    void riotValuesOf_WhenPositionIsMid_ReturnsBothMiddleAndMid() {
        // given & when
        List<String> riotValues = normalizer.riotValuesOf(ChampionPosition.MID);

        // then
        assertThat(riotValues).containsExactlyInAnyOrder("MID", "MIDDLE");
    }

    @Test
    @DisplayName("SUPPORT는 Riot 원시값 UTILITY와 SUPPORT를 모두 조회 대상으로 되돌린다")
    void riotValuesOf_WhenPositionIsSupport_ReturnsBothUtilityAndSupport() {
        // given & when
        List<String> riotValues = normalizer.riotValuesOf(ChampionPosition.SUPPORT);

        // then
        assertThat(riotValues).containsExactlyInAnyOrder("SUPPORT", "UTILITY");
    }

    @Test
    @DisplayName("Riot 원시값이 enum 이름과 같은 포지션은 자기 이름 하나만 반환한다")
    void riotValuesOf_WhenPositionHasNoRiotAlias_ReturnsOnlyItsOwnName() {
        // given & when & then
        assertThat(normalizer.riotValuesOf(ChampionPosition.TOP)).containsExactly("TOP");
        assertThat(normalizer.riotValuesOf(ChampionPosition.JUNGLE)).containsExactly("JUNGLE");
        assertThat(normalizer.riotValuesOf(ChampionPosition.BOTTOM)).containsExactly("BOTTOM");
    }

    @Test
    @DisplayName("되돌린 Riot 원시값은 다시 정규화하면 원래 포지션으로 돌아온다")
    void riotValuesOf_WhenNormalizedBack_ReturnsOriginalPosition() {
        // given & when & then: 역매핑이 normalize와 일관됨을 모든 포지션에 대해 확인한다
        for (ChampionPosition position : ChampionPosition.values()) {
            assertThat(normalizer.riotValuesOf(position))
                    .allSatisfy(riotValue -> assertThat(normalizer.normalize(riotValue)).contains(position));
        }
    }
}
