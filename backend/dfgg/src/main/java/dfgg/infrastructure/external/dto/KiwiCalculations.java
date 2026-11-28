package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.databind.JsonNode;
import dfgg.domain.aram.CalculationPart;
import dfgg.domain.aram.CalculationPart.Constant;
import dfgg.domain.aram.CalculationPart.DataValue;
import dfgg.domain.aram.CalculationPart.LevelRange;
import dfgg.domain.aram.CalculationPart.StatScaling;
import dfgg.domain.aram.CalculationPart.Unsupported;
import dfgg.domain.aram.ChampionStat;
import dfgg.domain.aram.SpellCalculation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 주문의 {@code mSpellCalculations}를 계산식으로 바꾼다. 표기 규칙이 없는 필드가 있으면 풀지 않는 계산식이 된다. */
final class KiwiCalculations {

    private static final String CALCULATION = "GameCalculation";
    /** 근접·원거리 값이 다른 계산식. CommunityDragon이 타입 이름을 풀지 못해 해시로 남아 있다. */
    private static final String RANGED_CALCULATION = "{e9a3c91d}";
    private static final Set<String> BREAKPOINT_FIELDS = Set.of("mLevel", "mAdditionalBonusAtThisLevel", "__type");
    private static final int TOTAL_STAT_FORMULA = 0;
    private static final int BONUS_STAT_FORMULA = 2;

    private KiwiCalculations() {
    }

    static Map<String, SpellCalculation> of(JsonNode calculations) {
        Map<String, SpellCalculation> converted = new HashMap<>();
        calculations.properties().forEach(entry -> converted.put(entry.getKey(), calculationOf(entry.getValue())));
        return converted;
    }

    /** 계산식 전체에 곱하는 {@code mMultiplier}는 표기 규칙이 없다. */
    private static SpellCalculation calculationOf(JsonNode calculation) {
        String type = calculation.path("__type").asText();
        if (calculation.has("mMultiplier")) {
            return SpellCalculation.unsupported(type + ".mMultiplier");
        }
        List<CalculationPart> parts = new ArrayList<>();
        calculation.path("mFormulaParts").forEach(part -> parts.add(partOf(part)));
        boolean percent = calculation.path("mDisplayAsPercent").asBoolean(false);
        return switch (type) {
            case CALCULATION -> new SpellCalculation(parts, percent);
            case RANGED_CALCULATION -> SpellCalculation.ranged(parts, percent,
                    partOf(calculation.path("mRangedMultiplier")));
            default -> SpellCalculation.unsupported(type);
        };
    }

    private static CalculationPart partOf(JsonNode part) {
        String type = part.path("__type").asText();
        return switch (type) {
            case "NumberCalculationPart" -> new Constant(part.path("mNumber").asDouble(0));
            case "NamedDataValueCalculationPart" -> new DataValue(part.path("mDataValue").asText(""));
            case "ByCharLevelInterpolationCalculationPart" ->
                    new LevelRange(part.path("mStartValue").asDouble(0), part.path("mEndValue").asDouble(0));
            case "ByCharLevelBreakpointsCalculationPart" -> breakpointsOf(part);
            case "StatByNamedDataValueCalculationPart" ->
                    scalingOf(part, new DataValue(part.path("mDataValue").asText("")));
            case "StatByCoefficientCalculationPart" ->
                    scalingOf(part, new Constant(part.path("mCoefficient").asDouble(0)));
            default -> new Unsupported(type);
        };
    }

    /**
     * 능력치 번호와 공식이 없으면 0, 즉 전체 주문력이다. 기본 능력치 공식(1)과 {@code OutputType}은 표기 규칙이 없어 풀지 않는다.
     */
    private static CalculationPart scalingOf(JsonNode part, CalculationPart coefficient) {
        int formula = part.path("mStatFormula").asInt(TOTAL_STAT_FORMULA);
        if (part.has("OutputType") || (formula != TOTAL_STAT_FORMULA && formula != BONUS_STAT_FORMULA)) {
            return new Unsupported(part.path("__type").asText() + " formula=" + formula);
        }
        int statCode = part.path("mStat").asInt(0);
        return ChampionStat.of(statCode)
                .<CalculationPart>map(stat -> new StatScaling(stat, formula == BONUS_STAT_FORMULA, coefficient))
                .orElseGet(() -> new Unsupported("mStat " + statCode));
    }

    /** 레벨마다 더하는 값({@code mInitialBonusPerLevel} 등)은 범위의 끝을 알 수 없어 풀지 않는다. */
    private static CalculationPart breakpointsOf(JsonNode part) {
        if (part.has("mInitialBonusPerLevel")) {
            return new Unsupported("ByCharLevelBreakpointsCalculationPart.mInitialBonusPerLevel");
        }
        double start = part.path("mLevel1Value").asDouble(0);
        double end = start;
        for (JsonNode breakpoint : part.path("mBreakpoints")) {
            if (!breakpoint.properties().stream().map(Map.Entry::getKey).allMatch(BREAKPOINT_FIELDS::contains)) {
                return new Unsupported("Breakpoint");
            }
            end += breakpoint.path("mAdditionalBonusAtThisLevel").asDouble(0);
        }
        return new LevelRange(start, end);
    }
}
