package dfgg.application.sequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import dfgg.application.utils.WilsonScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WilsonScoreCalculatorTest {

    private final WilsonScoreCalculator calculator = new WilsonScoreCalculator();

    @Test
    @DisplayName("95% 신뢰수준에서 성공 50/100은 잘 알려진 Wilson 하한값(약 0.4038)을 반환한다")
    void lowerBound_WithFiftyOutOfHundredWins_ReturnsKnownWilsonLowerBound() {
        double lowerBound = calculator.lowerBound(50, 100);

        assertThat(lowerBound).isCloseTo(0.4038, offset(0.001));
    }

    @Test
    @DisplayName("95% 신뢰수준에서 성공 5/10은 잘 알려진 Wilson 하한값(약 0.2367)을 반환한다")
    void lowerBound_WithFiveOutOfTenWins_ReturnsKnownWilsonLowerBound() {
        double lowerBound = calculator.lowerBound(5, 10);

        assertThat(lowerBound).isCloseTo(0.2367, offset(0.001));
    }

    @Test
    @DisplayName("같은 승률이라도 표본이 클수록 Wilson 하한값이 더 높다(불확실성이 줄어듦)")
    void lowerBound_WithLargerSampleAtSameWinRate_IsHigherThanSmallerSample() {
        double smallSample = calculator.lowerBound(5, 10);
        double largeSample = calculator.lowerBound(50, 100);

        assertThat(largeSample).isGreaterThan(smallSample);
    }

    @Test
    @DisplayName("한 번도 이기지 못했다면 Wilson 하한값은 0이다")
    void lowerBound_WithZeroWins_ReturnsZero() {
        double lowerBound = calculator.lowerBound(0, 10);

        assertThat(lowerBound).isEqualTo(0.0);
    }

    @Test
    @DisplayName("표본이 0건이면 NaN을 전파하지 않고 신뢰 하한 0을 반환한다")
    void lowerBound_WithZeroTotal_ReturnsZeroInsteadOfNaN() {
        double lowerBound = calculator.lowerBound(0, 0);

        assertThat(lowerBound).isEqualTo(0.0);
    }
}
