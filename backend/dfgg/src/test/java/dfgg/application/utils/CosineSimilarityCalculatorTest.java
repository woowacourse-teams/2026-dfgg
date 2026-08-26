package dfgg.application.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CosineSimilarityCalculatorTest {

    private final CosineSimilarityCalculator calculator = new CosineSimilarityCalculator();

    @Test
    @DisplayName("완전히 같은 방향을 가리키는 벡터의 코사인 유사도는 1이다")
    void compute_WhenVectorsPointSameDirection_ReturnsOne() {
        // given & when
        double similarity = calculator.compute(List.of(1.0, 0.0), List.of(2.0, 0.0));

        // then
        assertThat(similarity).isCloseTo(1.0, offset(0.0001));
    }

    @Test
    @DisplayName("서로 직교하는 벡터의 코사인 유사도는 0이다")
    void compute_WhenVectorsAreOrthogonal_ReturnsZero() {
        // given & when
        double similarity = calculator.compute(List.of(1.0, 0.0), List.of(0.0, 1.0));

        // then
        assertThat(similarity).isCloseTo(0.0, offset(0.0001));
    }

    @Test
    @DisplayName("정반대 방향을 가리키는 벡터의 코사인 유사도는 -1이다")
    void compute_WhenVectorsPointOppositeDirections_ReturnsNegativeOne() {
        // given & when
        double similarity = calculator.compute(List.of(1.0, 0.0), List.of(-1.0, 0.0));

        // then
        assertThat(similarity).isCloseTo(-1.0, offset(0.0001));
    }

    @Test
    @DisplayName("잘 알려진 3-4-5 벡터쌍의 코사인 유사도(0.6)를 정확히 계산한다")
    void compute_WithKnownVectorPair_ReturnsExpectedSimilarity() {
        // given & when
        double similarity = calculator.compute(List.of(1.0, 0.0), List.of(0.6, 0.8));

        // then
        assertThat(similarity).isCloseTo(0.6, offset(0.0001));
    }

    @Test
    @DisplayName("여러 벡터 중 코사인 유사도가 가장 높은 값을 반환한다 (평균으로 뭉치지 않고 개별 비교)")
    void maxSimilarity_WhenMultipleOtherVectors_ReturnsHighestSimilarity() {
        // given
        List<Double> item = List.of(0.6, 0.8);
        List<List<Double>> others = List.of(
                List.of(1.0, 0.0),   // cosine = 0.6
                List.of(0.0, 1.0)    // cosine = 0.8
        );

        // when
        double maxSimilarity = calculator.maxSimilarity(item, others);

        // then
        assertThat(maxSimilarity).isCloseTo(0.8, offset(0.0001));
    }

    @Test
    @DisplayName("비교 대상 벡터가 하나뿐이면 그 값을 그대로 반환한다")
    void maxSimilarity_WhenOnlyOneOtherVector_ReturnsThatSimilarity() {
        // given
        List<Double> item = List.of(1.0, 0.0);
        List<List<Double>> others = List.of(List.of(0.6, 0.8));

        // when
        double maxSimilarity = calculator.maxSimilarity(item, others);

        // then
        assertThat(maxSimilarity).isCloseTo(0.6, offset(0.0001));
    }
}
