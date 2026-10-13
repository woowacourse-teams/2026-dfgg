package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DfScoreTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "74.25, 74.3",
            "74.24, 74.2",
            "66.639496, 66.6",
            "50, 50.0",
    })
    @DisplayName("점수는 소수 첫째 자리로 반올림한다")
    void value_WhenFractional_RoundToOneDecimalHalfUp(double raw, double expected) {
        // when
        double value = new DfScore(raw).value();

        // then
        assertThat(value).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "-12.5, 0.0",
            "131.7, 100.0",
    })
    @DisplayName("0 미만은 0, 100 초과는 100으로 자른다")
    void value_WhenOutOfRange_ClampToZeroAndHundred(double raw, double expected) {
        // when
        double value = new DfScore(raw).value();

        // then
        assertThat(value).isEqualTo(expected);
    }
}
