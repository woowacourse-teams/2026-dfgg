package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoreCurveTest {

    @Test
    @DisplayName("기대만큼 하면 0.5다")
    void compare_WhenActualEqualsExpected_ReturnHalf() {
        // when
        double score = ScoreCurve.compare(4000, 4000);

        // then
        assertThat(score).isEqualTo(0.5);
    }

    @Test
    @DisplayName("기대의 두 배면 0.8이다")
    void compare_WhenActualIsTwiceExpected_ReturnPointEight() {
        // when
        double score = ScoreCurve.compare(2 * 10000 + 1, 10000);

        // then
        assertThat(score).isCloseTo(0.8, within(1e-9));
    }

    @Test
    @DisplayName("값과 기대가 모두 0이어도 0.5다 — 둘 다 1을 더해 0으로 나누지 않는다")
    void compare_WhenBothAreZero_ReturnHalf() {
        // when
        double score = ScoreCurve.compare(0, 0);

        // then
        assertThat(score).isEqualTo(0.5);
    }
}
