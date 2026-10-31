package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TierScopeTest {

    private static final String SOURCE = "recommendation.v3.tiers";

    @Test
    @DisplayName("빈 목록은 범위가 될 수 없다")
    void of_WhenEmpty_ThrowIllegalArgumentException() {
        // when & then
        assertThatThrownBy(() -> TierScope.of(List.of(), SOURCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SOURCE);
    }

    @Test
    @DisplayName("null은 범위가 될 수 없다")
    void of_WhenNull_ThrowIllegalArgumentException() {
        // when & then
        assertThatThrownBy(() -> TierScope.of(null, SOURCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SOURCE);
    }

    @Test
    @DisplayName("모르는 티어가 있으면 거부한다 — 오타가 통과하면 그 티어가 통계에서 통째로 빠진다")
    void of_WhenUnsupportedTier_ThrowIllegalArgumentExceptionNamingTheSource() {
        // when & then
        assertThatThrownBy(() -> TierScope.of(List.of("EMERALD", "EMERLAD"), SOURCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SOURCE)
                .hasMessageContaining("EMERLAD");
    }

    @Test
    @DisplayName("대소문자와 앞뒤 공백을 흡수한다")
    void of_WhenCasingAndSpaces_NormalizeToUpperCase() {
        // when
        TierScope scope = TierScope.of(List.of(" emerald ", "Diamond"), SOURCE);

        // then
        assertThat(scope.values()).containsExactly("EMERALD", "DIAMOND");
    }

    @Test
    @DisplayName("중복을 제거한다 — IN 절에 같은 값이 두 번 들어갈 이유가 없다")
    void of_WhenDuplicated_RemoveDuplicates() {
        // when
        TierScope scope = TierScope.of(List.of("EMERALD", "emerald"), SOURCE);

        // then
        assertThat(scope.values()).containsExactly("EMERALD");
    }

    @Test
    @DisplayName("범위에 든 티어인지 답한다")
    void contains_WhenTierInScope_ReturnTrue() {
        // given
        TierScope scope = TierScope.of(List.of("EMERALD", "CHALLENGER"), SOURCE);

        // when & then
        assertThat(scope.contains("emerald")).isTrue();
        assertThat(scope.contains("PLATINUM")).isFalse();
        assertThat(scope.contains(null)).isFalse();
    }

    @Test
    @DisplayName("values는 밖에서 못 바꾼다")
    void values_WhenModified_ThrowUnsupportedOperationException() {
        // given
        TierScope scope = TierScope.of(List.of("EMERALD"), SOURCE);

        // when & then
        assertThatThrownBy(() -> scope.values().add("PLATINUM"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
