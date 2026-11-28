package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.aram.CalculationPart.Constant;
import dfgg.domain.aram.CalculationPart.DataValue;
import dfgg.domain.aram.CalculationPart.LevelRange;
import dfgg.domain.aram.CalculationPart.StatScaling;
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
    @DisplayName("능력치 계수는 기본값 뒤에 괄호로 붙인다")
    void format_WhenStatScaling_AppendsCoefficient() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new LevelRange(150.0, 450.0),
                new StatScaling(ChampionStat.HEALTH, false, new DataValue("HealthScalar"))), false);
        Map<String, Double> dataValues = Map.of("HealthScalar", 0.03999999910593033);

        // when, then
        assertThat(calculation.format(dataValues, 1, false, Language.KO_KR)).contains("150~450(레벨에 따라) (+체력의 4%)");
        assertThat(calculation.format(dataValues, 1, false, Language.EN_US))
                .contains("150~450 (based on level) (+4% Health)");
    }

    @Test
    @DisplayName("추가 능력치 계수는 '추가'를 붙인다")
    void format_WhenBonusStatScaling_WritesBonus() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new LevelRange(25.0, 125.0),
                new StatScaling(ChampionStat.ARMOR, true, new DataValue("ArmorPercent"))), false);
        Map<String, Double> dataValues = Map.of("ArmorPercent", 2.200000047683716);

        // when, then
        assertThat(calculation.format(dataValues, 1, false, Language.KO_KR))
                .contains("25~125(레벨에 따라) (+추가 방어력의 220%)");
        assertThat(calculation.format(dataValues, 1, false, Language.EN_US))
                .contains("25~125 (based on level) (+220% bonus Armor)");
    }

    @Test
    @DisplayName("백분율 계산식의 능력치 계수는 능력치 100당 값으로 쓴다")
    void format_WhenPercentStatScaling_WritesPerHundred() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new DataValue("CritChance"),
                new StatScaling(ChampionStat.ABILITY_POWER, false, new Constant(0.00044999999227002263))), true);
        Map<String, Double> dataValues = Map.of("CritChance", 0.25);

        // when, then
        assertThat(calculation.format(dataValues, 1, false, Language.KO_KR)).contains("25% (+주문력 100당 4.5%)");
        assertThat(calculation.format(dataValues, 1, false, Language.EN_US)).contains("25% (+4.5% per 100 AP)");
    }

    @Test
    @DisplayName("원문에서 뒤에 퍼센트가 와도 계수 표기는 그대로다")
    void format_WhenPercentFollowsWithStatScaling_MovesPercentToBase() {
        // given
        SpellCalculation calculation = new SpellCalculation(List.of(new DataValue("InitialHS"),
                new StatScaling(ChampionStat.ARMOR, true, new DataValue("ConversionRate")),
                new StatScaling(ChampionStat.MAGIC_RESIST, true, new DataValue("ConversionRate"))), false);
        Map<String, Double> dataValues = Map.of("InitialHS", 25.0, "ConversionRate", 0.3499999940395355);

        // when, then
        assertThat(calculation.format(dataValues, 1, true, Language.KO_KR))
                .contains("25% (+추가 방어력의 35%) (+추가 마법 저항력의 35%)");
    }

    @Test
    @DisplayName("원거리 배율 계산식은 원거리 값을 함께 쓴다")
    void format_WhenRanged_WritesRangedValue() {
        // given
        SpellCalculation calculation = SpellCalculation.ranged(List.of(new Constant(150.0)), false,
                new Constant(0.6669999957084656));

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).contains("150(원거리 100)");
        assertThat(calculation.format(Map.of(), 1, false, Language.EN_US)).contains("150 (Ranged: 100)");
    }

    @Test
    @DisplayName("백분율 원거리 배율 계산식은 두 값 모두 퍼센트로 쓴다")
    void format_WhenPercentRanged_WritesBothPercent() {
        // given
        SpellCalculation calculation = SpellCalculation.ranged(List.of(new Constant(0.75)), true,
                new Constant(0.5329999923706055));

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).contains("75%(원거리 40%)");
    }

    @Test
    @DisplayName("원거리 배율 계산식에 레벨 범위가 있으면 쓰지 않는다")
    void format_WhenRangedWithLevelRange_IsEmpty() {
        // given
        SpellCalculation calculation = SpellCalculation.ranged(List.of(new LevelRange(10.0, 20.0)), false,
                new Constant(0.5));

        // when, then
        assertThat(calculation.format(Map.of(), 1, false, Language.KO_KR)).isEmpty();
    }

    @Test
    @DisplayName("원거리 배율 계산식에 능력치 계수가 있으면 쓰지 않는다")
    void format_WhenRangedWithStatScaling_IsEmpty() {
        // given
        SpellCalculation calculation = SpellCalculation.ranged(List.of(new Constant(10.0),
                new StatScaling(ChampionStat.ATTACK_DAMAGE, true, new Constant(0.5))), false, new Constant(0.5));

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
