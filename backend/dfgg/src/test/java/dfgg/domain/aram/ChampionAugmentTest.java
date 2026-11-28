package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChampionAugmentTest {

    @Test
    @DisplayName("픽률은 챔피언 판 수 중 증강을 가진 판 수의 비율(%)이다")
    void fromGames_CalculatesPickRate() {
        // when
        ChampionAugment championAugment = ChampionAugment.fromGames("16.21", 22, 1373, 1679, 10000);

        // then
        assertThat(championAugment.pickRate()).isCloseTo(16.79, within(0.0001));
    }

    @Test
    @DisplayName("증강을 가진 판 수가 챔피언 판 수보다 많으면 만들 수 없다")
    void fromGames_RejectsMoreGamesThanChampion() {
        // when & then
        assertThatThrownBy(() -> ChampionAugment.fromGames("16.21", 22, 1373, 10001, 10000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("챔피언 판 수가 0이면 만들 수 없다")
    void fromGames_RejectsZeroChampionGames() {
        // when & then
        assertThatThrownBy(() -> ChampionAugment.fromGames("16.21", 22, 1373, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판 수가 음수면 만들 수 없다")
    void fromGames_RejectsNegativeGames() {
        // when & then
        assertThatThrownBy(() -> ChampionAugment.fromGames("16.21", 22, 1373, -1, 10000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
