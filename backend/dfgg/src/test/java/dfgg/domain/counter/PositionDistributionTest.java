package dfgg.domain.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PositionDistributionTest {

    @Test
    @DisplayName("다섯 라인을 모두 담고, 나오지 않은 라인은 0%로 채운다")
    void shares_WhenSomePositionsMissing_FillAllFivePositions() {
        // given
        PositionDistribution distribution = PositionDistribution.of(Map.of(ChampionPosition.MID, 3));

        // when
        List<PositionShare> shares = distribution.shares();

        // then
        assertThat(shares).extracting(PositionShare::position)
                .containsExactlyInAnyOrder(ChampionPosition.values());
        assertThat(shares).filteredOn(share -> share.position() != ChampionPosition.MID)
                .allSatisfy(share -> {
                    assertThat(share.rate()).isZero();
                    assertThat(share.games()).isZero();
                });
    }

    @Test
    @DisplayName("비율을 소수 첫째 자리 %로 계산해 내림차순으로 놓는다")
    void shares_WhenGamesGiven_SortByRateDescendingWithOneDecimal() {
        // given
        PositionDistribution distribution = PositionDistribution.of(Map.of(
                ChampionPosition.TOP, 34,
                ChampionPosition.MID, 465,
                ChampionPosition.SUPPORT, 4,
                ChampionPosition.BOTTOM, 1));

        // when
        List<PositionShare> shares = distribution.shares();

        // then
        assertThat(shares).extracting(PositionShare::position, PositionShare::rate, PositionShare::games)
                .containsExactly(
                        tuple(ChampionPosition.MID, 92.3, 465),
                        tuple(ChampionPosition.TOP, 6.7, 34),
                        tuple(ChampionPosition.SUPPORT, 0.8, 4),
                        tuple(ChampionPosition.BOTTOM, 0.2, 1),
                        tuple(ChampionPosition.JUNGLE, 0.0, 0));
    }

    @Test
    @DisplayName("비율이 같으면 라인 순서(TOP·JUNGLE·MID·BOTTOM·SUPPORT)로 놓는다")
    void shares_WhenRatesTie_OrderByPosition() {
        // given
        PositionDistribution distribution = PositionDistribution.of(Map.of(
                ChampionPosition.SUPPORT, 1,
                ChampionPosition.TOP, 1));

        // when
        List<PositionShare> shares = distribution.shares();

        // then
        assertThat(shares).extracting(PositionShare::position)
                .containsExactly(ChampionPosition.TOP, ChampionPosition.SUPPORT,
                        ChampionPosition.JUNGLE, ChampionPosition.MID, ChampionPosition.BOTTOM);
    }

    @Test
    @DisplayName("현재 패치에 나온 기록이 없으면 빈 목록이다")
    void shares_WhenNoGames_ReturnEmpty() {
        // given
        PositionDistribution distribution = PositionDistribution.of(Map.of());

        // when
        List<PositionShare> shares = distribution.shares();

        // then
        assertThat(shares).isEmpty();
    }
}
