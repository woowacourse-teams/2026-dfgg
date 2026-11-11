package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.aram.CalculationPart.Constant;
import dfgg.domain.aram.CalculationPart.DataValue;
import dfgg.domain.aram.CalculationPart.LevelRange;
import dfgg.domain.aram.CalculationPart.Unsupported;
import dfgg.domain.language.Language;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 값은 16.22 증강 계산식에서 가져왔다. */
class SpellCalculationTest {

    @Test
    @DisplayName("레벨에 따라 바뀌는 값은 범위와 레벨 문구로 쓴다")
    void format_WhenByLevel_WritesRangeWithLevelNote() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new LevelRange(20.0, 80.0)), false);

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).contains("20~80(레벨에 따라)");
        assertThat(calculation.format(Map.of(), 1, false, Language.EN_US)).contains("20~80 (based on level)");
    }

    @Test
    @DisplayName("백분율 계산식은 100을 곱해 숫자마다 퍼센트를 붙인다")
    void format_WhenPercent_MultipliesAndAppendsPercent() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new LevelRange(0.1, 0.3)), true);

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).contains("10%~30%(레벨에 따라)");
    }

    @Test
    @DisplayName("하한과 상한이 같으면 숫자 하나로 쓴다")
    void format_WhenFixed_WritesSingleNumber() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new Constant(0.25)), true);

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).contains("25%");
    }

    @Test
    @DisplayName("숫자와 데이터값을 더한다")
    void format_SumsConstantAndDataValue() {
        // given
        SpellCalculation calculation = new SpellCalculation(
                List.of(new Constant(2.0), new DataValue("AbilityHasteReward")), false);

        // when, then
        assertThat(calculation.format(Map.of("AbilityHasteReward", 3.0), 1, false, Language.KO_KR)).contains("5");
    }

    @Test
    @DisplayName("자리표시자의 배수를 곱한다")
    void format_AppliesMultiplier() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new LevelRange(0.4, 1.0)), false);

        // when, then
        assertThat(calculation.format(Map.of(), 100, false, Language.KO_KR)).contains("40~100(레벨에 따라)");
    }

    @Test
    @DisplayName("원문에서 뒤에 퍼센트가 오면 숫자마다 붙인다")
    void format_WhenPercentFollows_AppendsPercent() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new DataValue("InitialHS")), false);

        // when, then
        assertThat(calculation.format(Map.of("InitialHS", 25.0), 1, true, Language.KO_KR)).contains("25%");
    }

    @Test
    @DisplayName("백분율 계산식 뒤에 퍼센트가 와도 두 번 붙이지 않는다")
    void format_WhenPercentAndPercentFollows_AppendsOnce() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new Constant(0.25)), true);

        // when, then
        assertThat(calculation.format(Map.of(), 1, true, Language.KO_KR)).contains("25%");
    }

    @Test
    @DisplayName("데이터값을 찾지 못하면 쓰지 않는다")
    void format_WhenDataValueMissing_IsEmpty() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new DataValue("QuestRequirement")), false);

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).isEmpty();
    }

    @Test
    @DisplayName("풀지 못하는 요소가 하나라도 있으면 쓰지 않는다")
    void format_WhenUnsupportedPart_IsEmpty() {
        // given
        SpellCalculation calculation = new SpellCalculation(
                List.of(new LevelRange(150.0, 450.0), new Unsupported("StatByNamedDataValueCalculationPart")), false);

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).isEmpty();
    }

    @Test
    @DisplayName("풀지 못하는 계산식은 쓰지 않는다")
    void format_WhenUnsupportedCalculation_IsEmpty() {
        // given
        SpellCalculation calculation = SpellCalculation.unsupported("GameCalculationModified");

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).isEmpty();
    }
}
