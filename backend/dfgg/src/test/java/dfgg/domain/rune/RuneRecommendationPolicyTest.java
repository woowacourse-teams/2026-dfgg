package dfgg.domain.rune;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuneRecommendationPolicyTest {

    @Test
    @DisplayName("기준 판 수와 페이지 수는 1 이상이어야 한다")
    void create_WhenValueIsNotPositive_Throws() {
        assertThatThrownBy(() -> new RuneRecommendationPolicy(0, 5, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuneRecommendationPolicy(100, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuneRecommendationPolicy(100, 5, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
