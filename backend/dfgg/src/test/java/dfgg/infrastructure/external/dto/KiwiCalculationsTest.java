package dfgg.infrastructure.external.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.aram.SpellValues;
import dfgg.domain.language.Language;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 계산식 JSON 형식은 16.22 {@code kiwi.bin.json}의 {@code mSpellCalculations}와 같다. */
class KiwiCalculationsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("레벨 구간은 처음 값부터 모든 구간 추가값을 더한 값까지다")
    void of_WhenBreakpoints_WritesRange() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mLevel1Value": 2.0, "mBreakpoints": [
                    {"mLevel": 11, "mAdditionalBonusAtThisLevel": 1.0, "__type": "Breakpoint"}],
                  "__type": "ByCharLevelBreakpointsCalculationPart"}],
                 "__type": "GameCalculation"}
                """;

        // when
        Optional<String> text = format(calculation, Map.of());

        // then
        assertThat(text).contains("2~3(레벨에 따라)");
    }

    @Test
    @DisplayName("레벨마다 더하는 값이 있는 레벨 구간은 풀지 않는다")
    void of_WhenBonusPerLevel_IsUnsupported() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mLevel1Value": 2.0, "mInitialBonusPerLevel": 0.5,
                  "__type": "ByCharLevelBreakpointsCalculationPart"}],
                 "__type": "GameCalculation"}
                """;

        // when, then
        assertThat(format(calculation, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("표에 없는 구간 필드가 있으면 풀지 않는다")
    void of_WhenUnknownBreakpointField_IsUnsupported() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mLevel1Value": 2.0, "mBreakpoints": [
                    {"mLevel": 11, "mBonusPerLevelAtAndAfter": 1.0, "__type": "Breakpoint"}],
                  "__type": "ByCharLevelBreakpointsCalculationPart"}],
                 "__type": "GameCalculation"}
                """;

        // when, then
        assertThat(format(calculation, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("계산식 전체에 곱하는 값이 있으면 풀지 않는다")
    void of_WhenMultiplier_IsUnsupported() throws Exception {
        // given
        String calculation = """
                {"mMultiplier": {"mDataValue": "AbilityHasteReward", "__type": "NamedDataValueCalculationPart"},
                 "mFormulaParts": [{"mNumber": 2.0, "__type": "NumberCalculationPart"}],
                 "__type": "GameCalculation"}
                """;

        // when, then
        assertThat(format(calculation, Map.of("AbilityHasteReward", 3.0))).isEmpty();
    }

    @Test
    @DisplayName("능력치 번호가 없으면 주문력 계수다")
    void of_WhenStatAbsent_IsAbilityPower() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mNumber": 0.1, "__type": "NumberCalculationPart"},
                  {"mCoefficient": 0.0003499999875202775, "__type": "StatByCoefficientCalculationPart"}],
                 "mDisplayAsPercent": true, "__type": "GameCalculation"}
                """;

        // when, then
        assertThat(format(calculation, Map.of())).contains("10% (+주문력 100당 3.5%)");
    }

    @Test
    @DisplayName("공식 2는 추가 능력치 계수다")
    void of_WhenBonusFormula_WritesBonus() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mStat": 12, "mStatFormula": 2, "mDataValue": "ShieldRatioBHealth",
                  "__type": "StatByNamedDataValueCalculationPart"}],
                 "__type": "GameCalculation"}
                """;

        // when, then
        assertThat(format(calculation, Map.of("ShieldRatioBHealth", 0.1))).contains("0 (+추가 체력의 10%)");
    }

    @Test
    @DisplayName("표에 없는 능력치 번호는 풀지 않는다")
    void of_WhenStatUnknown_IsUnsupported() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mStat": 8, "mDataValue": "AdditionalMisDivision",
                  "__type": "StatByNamedDataValueCalculationPart"}],
                 "__type": "GameCalculation"}
                """;

        // when, then
        assertThat(format(calculation, Map.of("AdditionalMisDivision", 3.0))).isEmpty();
    }

    @Test
    @DisplayName("기본 능력치 공식은 풀지 않는다")
    void of_WhenBaseStatFormula_IsUnsupported() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mStat": 2, "mStatFormula": 1, "mCoefficient": 0.5,
                  "__type": "StatByCoefficientCalculationPart"}],
                 "__type": "GameCalculation"}
                """;

        // when, then
        assertThat(format(calculation, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("OutputType이 있는 계수는 풀지 않는다")
    void of_WhenOutputType_IsUnsupported() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mStat": 2, "mCoefficient": 0.5, "OutputType": 1,
                  "__type": "StatByCoefficientCalculationPart"}],
                 "__type": "GameCalculation"}
                """;

        // when, then
        assertThat(format(calculation, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("원거리 배율 계산식을 읽는다")
    void of_WhenRanged_WritesRangedValue() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mNumber": 150.0, "__type": "NumberCalculationPart"}],
                 "mRangedMultiplier": {"mNumber": 0.6669999957084656, "__type": "NumberCalculationPart"},
                 "__type": "{e9a3c91d}"}
                """;

        // when, then
        assertThat(format(calculation, Map.of())).contains("150(원거리 100)");
    }

    @Test
    @DisplayName("모르는 계산식 타입은 풀지 않는다")
    void of_WhenUnknownType_IsUnsupported() throws Exception {
        // given
        String calculation = """
                {"mFormulaParts": [{"mNumber": 2.0, "__type": "NumberCalculationPart"}],
                 "__type": "GameCalculationModified"}
                """;

        // when, then
        assertThat(format(calculation, Map.of())).isEmpty();
    }

    private Optional<String> format(String calculation, Map<String, Double> dataValues) throws Exception {
        JsonNode calculations = JSON.readTree("{\"X\": " + calculation + "}");
        return new SpellValues(dataValues, KiwiCalculations.of(calculations)).format("X", 1, false, Language.KO_KR);
    }
}
