package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateTopKTest {

    @Test
    @DisplayName("generator마다 서로 다른 후보 수를 준다 — Recall@K 실험 결과가 여기 반영된다")
    void of_WhenEachGeneratorConfigured_ReturnsItsOwnTopK() {
        // given
        CandidateTopK topK = new CandidateTopK(20, 25, 30, 35);

        // when & then
        assertThat(topK.of(CandidateSource.BUILD)).isEqualTo(20);
        assertThat(topK.of(CandidateSource.SELF_SYNERGY)).isEqualTo(25);
        assertThat(topK.of(CandidateSource.ALLY_SYNERGY)).isEqualTo(30);
        assertThat(topK.of(CandidateSource.COUNTER)).isEqualTo(35);
    }

    @Test
    @DisplayName("후보 수가 1 미만이면 거부한다")
    void construct_WhenTopKIsNotPositive_ThrowsException() {
        assertThatThrownBy(() -> new CandidateTopK(0, 10, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CandidateTopK(10, 10, 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
