package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.databind.JsonNode;
import dfgg.domain.aram.CalculationPart;
import dfgg.domain.aram.CalculationPart.Constant;
import dfgg.domain.aram.CalculationPart.DataValue;
import dfgg.domain.aram.CalculationPart.LevelRange;
import dfgg.domain.aram.CalculationPart.Unsupported;
import dfgg.domain.aram.SpellCalculation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 주문의 {@code mSpellCalculations}를 계산식으로 바꾼다. 표기 규칙이 없는 필드가 있으면 풀지 않는 계산식이 된다. */
final class KiwiCalculations {

    private static final String CALCULATION = "GameCalculation";
    private static final Set<String> BREAKPOINT_FIELDS = Set.of("mLevel", "mAdditionalBonusAtThisLevel", "__type");

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
        if (!type.equals(CALCULATION) || calculation.has("mMultiplier")) {
            return SpellCalculation.unsupported(type);
        }
        List<CalculationPart> parts = new ArrayList<>();
        calculation.path("mFormulaParts").forEach(part -> parts.add(partOf(part)));
        return new SpellCalculation(parts, calculation.path("mDisplayAsPercent").asBoolean(false));
    }

    private static CalculationPart partOf(JsonNode part) {
        String type = part.path("__type").asText();
        return switch (type) {
            case "NumberCalculationPart" -> new Constant(part.path("mNumber").asDouble(0));
            case "NamedDataValueCalculationPart" -> new DataValue(part.path("mDataValue").asText(""));
            case "ByCharLevelInterpolationCalculationPart" ->
                    new LevelRange(part.path("mStartValue").asDouble(0), part.path("mEndValue").asDouble(0));
            case "ByCharLevelBreakpointsCalculationPart" -> breakpointsOf(part);
            default -> new Unsupported(type);
        };
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
