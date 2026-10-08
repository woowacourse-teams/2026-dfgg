package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class V3RecommendationPropertiesTest {

    @Test
    @DisplayName("설정한 티어를 범위로 만든다")
    void constructor_WhenTiersGiven_ExposeThemAsScope() {
        // when
        V3RecommendationProperties properties =
                new V3RecommendationProperties(List.of("emerald", "CHALLENGER"));

        // then
        assertThat(properties.scope().values()).containsExactly("EMERALD", "CHALLENGER");
    }

    @Test
    @DisplayName("모르는 티어면 기동을 거부하고 어느 설정인지 알려준다")
    void constructor_WhenUnsupportedTier_ThrowNamingTheProperty() {
        // when & then
        assertThatThrownBy(() -> new V3RecommendationProperties(List.of("EMERLAD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recommendation.v3.tiers");
    }
}
