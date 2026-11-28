package dfgg.domain.aram;

import dfgg.domain.language.Language;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 증강 주문의 계산식 하나({@code mSpellCalculations}). 요소를 더해 설명에 쓸 표기를 만든다.
 * <p>
 * 레벨에 따라 바뀌는 값은 게임 툴팁처럼 범위로, 능력치 비례 몫은 계수로 쓴다. 풀지 못하는 요소가 하나라도 있으면 쓰지 않는다.
 */
public final class SpellCalculation {

    private final List<CalculationPart> parts;
    private final boolean percent;
    /** 원거리 챔피언에게 곱하는 배율. 원거리 배율 계산식이 아니면 null이다. */
    private final CalculationPart rangedMultiplier;

    /** @param percent 게임이 값을 백분율로 보여 주는 계산식({@code mDisplayAsPercent}) */
    public SpellCalculation(List<CalculationPart> parts, boolean percent) {
        this(parts, percent, null);
    }

    private SpellCalculation(List<CalculationPart> parts, boolean percent, CalculationPart rangedMultiplier) {
        this.parts = List.copyOf(parts);
        this.percent = percent;
        this.rangedMultiplier = rangedMultiplier;
    }

    /** 근접·원거리 값이 다른 계산식. 기본값은 근접 값이다. */
    public static SpellCalculation ranged(List<CalculationPart> parts, boolean percent,
                                          CalculationPart rangedMultiplier) {
        return new SpellCalculation(parts, percent, rangedMultiplier);
    }

    /** 표기 규칙이 없는 계산식. 어떤 값으로도 쓰지 않는다. */
    public static SpellCalculation unsupported(String type) {
        return new SpellCalculation(List.of(new CalculationPart.Unsupported(type)), false);
    }

    /** @param percentFollows 원문에서 자리표시자 바로 뒤에 {@code %}가 있었는지 */
    Optional<String> format(Map<String, Double> dataValues, double multiplier, boolean percentFollows,
                            Language language) {
        String unit = percent || percentFollows ? "%" : "";
        double scale = percent ? multiplier * 100 : multiplier;
        return valueWith(dataValues).flatMap(value -> rangedNoteOf(value, dataValues, scale, unit, language)
                .map(rangedNote -> value.baseText(scale, unit) + levelNote(value, language) + rangedNote
                        + value.scalingText(percent, multiplier, language)));
    }

    private Optional<CalculationValue> valueWith(Map<String, Double> dataValues) {
        Optional<CalculationValue> sum = Optional.of(CalculationValue.fixed(0));
        for (CalculationPart part : parts) {
            sum = sum.flatMap(total -> part.valueWith(dataValues).map(total::plus));
        }
        return sum;
    }

    private static String levelNote(CalculationValue value, Language language) {
        if (!value.byLevel()) {
            return "";
        }
        if (language == Language.KO_KR) {
            return "(레벨에 따라)";
        }
        return " (based on level)";
    }

    /** 원거리 배율이 없으면 빈 문구다. 있으면 기본값이 숫자 하나여야 원거리 값을 정할 수 있다. */
    private Optional<String> rangedNoteOf(CalculationValue value, Map<String, Double> dataValues, double scale,
                                          String unit, Language language) {
        if (rangedMultiplier == null) {
            return Optional.of("");
        }
        if (!value.isConstant()) {
            return Optional.empty();
        }
        return rangedMultiplier.valueWith(dataValues)
                .filter(CalculationValue::isConstant)
                .map(ranged -> DisplayNumber.of(value.low() * ranged.low() * scale) + unit)
                .map(rangedValue -> language == Language.KO_KR
                        ? "(원거리 " + rangedValue + ")"
                        : " (Ranged: " + rangedValue + ")");
    }
}
