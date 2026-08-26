package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FinalScoreCalculatorTest {

    private final FinalScoreCalculator finalScoreCalculator = new FinalScoreCalculator();

    @Test
    @DisplayName("네 항목을 각자의 가중치로 곱해 더한 값을 반환한다")
    void calculate_WithGivenWeights_ReturnsWeightedSumOfFourTerms() {
        // given
        FinalScoreWeights weights = new FinalScoreWeights(1.0, 2.0, 3.0, 4.0);

        // when
        double finalScore = finalScoreCalculator.calculate(0.5, 0.6, 0.7, 0.8, weights);

        // then: 1.0*0.5 + 2.0*0.6 + 3.0*0.7 + 4.0*0.8 = 0.5+1.2+2.1+3.2 = 7.0
        assertThat(finalScore).isCloseTo(7.0, offset(0.0001));
    }

    @Test
    @DisplayName("가중치를 다르게 주면 결과도 그에 맞게 달라진다 (하드코딩된 가중치가 아님을 증명)")
    void calculate_WhenWeightsDiffer_ChangesResultAccordingly() {
        // given
        FinalScoreWeights equalWeights = new FinalScoreWeights(1.0, 1.0, 1.0, 1.0);
        FinalScoreWeights wilsonHeavyWeights = new FinalScoreWeights(10.0, 1.0, 1.0, 1.0);

        // when
        double withEqualWeights = finalScoreCalculator.calculate(0.5, 0.1, 0.1, 0.1, equalWeights);
        double withWilsonHeavyWeights = finalScoreCalculator.calculate(0.5, 0.1, 0.1, 0.1, wilsonHeavyWeights);

        // then
        assertThat(withWilsonHeavyWeights).isGreaterThan(withEqualWeights);
    }

    @Test
    @DisplayName("탐색 구역처럼 Wilson 항이 없는 후보는 wilsonLowerBound에 0을 넣어 그 항을 배제한다")
    void calculate_WhenWilsonLowerBoundIsZero_ExcludesWilsonTermFromScore() {
        // given
        FinalScoreWeights weights = new FinalScoreWeights(5.0, 1.0, 1.0, 1.0);

        // when: 탐색 구역 후보는 Wilson 점수가 없으므로 0을 전달
        double finalScore = finalScoreCalculator.calculate(0.0, 0.6, 0.7, 0.8, weights);

        // then: 5.0*0 + 1.0*0.6 + 1.0*0.7 + 1.0*0.8 = 2.1
        assertThat(finalScore).isCloseTo(2.1, offset(0.0001));
    }
}
