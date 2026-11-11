package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpellValuesTest {

    @Test
    @DisplayName("값은 소수 첫째 자리에서 반올림하고 끝의 0을 지운다")
    void format_RoundsToOneDecimal() {
        // given
        SpellValues values = new SpellValues(Map.of("Ratio", 0.04449999928474426, "Amp", 0.20000000298023224));

        // when, then
        assertThat(values.format("Ratio", 100, false)).contains("4.4");
        assertThat(values.format("Amp", 100, false)).contains("20");
    }

    @Test
    @DisplayName("반올림은 0.05에서 올린다")
    void format_RoundsHalfUp() {
        // given
        SpellValues values = new SpellValues(Map.of("Duration", 2.25));

        // when, then
        assertThat(values.format("Duration", 1, false)).contains("2.3");
    }

    @Test
    @DisplayName("뒤에 퍼센트가 오면 값 바로 뒤에 붙인다")
    void format_WhenPercentFollows_AppendsPercent() {
        // given
        SpellValues values = new SpellValues(Map.of("Amp", 0.2));

        // when, then
        assertThat(values.format("Amp", 100, true)).contains("20%");
    }

    @Test
    @DisplayName("없는 이름이면 비어 있다")
    void format_WhenUndefined_ReturnsEmpty() {
        // given
        SpellValues values = new SpellValues(Map.of());

        // when, then
        assertThat(values.format("QuestRequirement", 1, false)).isEmpty();
    }
}
