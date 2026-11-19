package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseScoreTest {

    @Test
    @DisplayName("개인 지표와 팀 기여가 모두 0.5면 50점이다")
    void of_WhenBothAreHalf_ReturnFifty() {
        // when
        double baseScore = BaseScore.of(0.5, 0.5);

        // then
        assertThat(baseScore).isCloseTo(50.0, within(1e-9));
    }

    @Test
    @DisplayName("기본점수는 50 + 150 × (0.6 × 개인 지표 + 0.4 × 팀 기여 − 0.5)다")
    void of_WhenIndividualAndTeamGiven_WeightSixToFour() {
        // when
        double baseScore = BaseScore.of(0.7, 0.4);

        // then
        assertThat(baseScore).isCloseTo(50 + 150 * (0.6 * 0.7 + 0.4 * 0.4 - 0.5), within(1e-9));
    }
}
