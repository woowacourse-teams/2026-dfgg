package dfgg.domain.aram;

import dfgg.domain.language.Language;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 증강 주문의 계산식 하나({@code mSpellCalculations}). 요소를 더해 설명에 쓸 표기를 만든다.
 * <p>
 * 레벨에 따라 바뀌는 값은 게임 툴팁처럼 범위로 쓴다. 풀지 못하는 요소가 하나라도 있으면 쓰지 않는다.
 */
public final class SpellCalculation {

    private final List<CalculationPart> parts;
    private final boolean percent;

    /** @param percent 게임이 값을 백분율로 보여 주는 계산식({@code mDisplayAsPercent}) */
    public SpellCalculation(List<CalculationPart> parts, boolean percent) {
        this.parts = List.copyOf(parts);
        this.percent = percent;
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
        return rangeWith(dataValues).map(range -> range.text(scale, unit) + levelNote(range, language));
    }

    private Optional<CalculationRange> rangeWith(Map<String, Double> dataValues) {
        Optional<CalculationRange> sum = Optional.of(CalculationRange.fixed(0));
        for (CalculationPart part : parts) {
            sum = sum.flatMap(total -> part.rangeWith(dataValues).map(total::plus));
        }
        return sum;
    }

    private static String levelNote(CalculationRange range, Language language) {
        if (!range.byLevel()) {
            return "";
        }
        if (language == Language.KO_KR) {
            return "(레벨에 따라)";
        }
        return " (based on level)";
    }
}
