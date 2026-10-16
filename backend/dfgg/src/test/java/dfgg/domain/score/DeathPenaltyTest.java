package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dfgg.domain.champion.ChampionPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeathPenaltyTest {

    private final DeathPenalty deathPenalty = new DeathPenalty(0.278);

    private ScoringParticipant participantWithDeaths(int deaths) {
        return new ScoringParticipant(100, ChampionPosition.TOP, true,
                3, deaths, 5, 20000, 18000, 11000, 20, 3000, 5000);
    }

    @Test
    @DisplayName("허용 데스(분당 허용치 × 분) 이하로 죽으면 페널티는 0이다")
    void of_WhenDeathsWithinAllowance_ReturnZero() {
        // when
        double penalty = deathPenalty.of(participantWithDeaths(8), new GameDuration(1800));

        // then
        assertThat(penalty).isZero();
    }

    @Test
    @DisplayName("허용 데스를 넘은 1데스마다 5점을 깎는다")
    void of_WhenDeathsExceedAllowance_FivePerExcessDeath() {
        // when
        double penalty = deathPenalty.of(participantWithDeaths(10), new GameDuration(1800));

        // then
        assertThat(penalty).isCloseTo(5 * (10 - 0.278 * 30), within(1e-9));
    }

    @Test
    @DisplayName("페널티는 최대 30점이다")
    void of_WhenDeathsFarExceedAllowance_CapAtThirty() {
        // when
        double penalty = deathPenalty.of(participantWithDeaths(20), new GameDuration(1800));

        // then
        assertThat(penalty).isEqualTo(30.0);
    }
}
