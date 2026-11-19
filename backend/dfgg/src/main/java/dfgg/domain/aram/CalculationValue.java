package dfgg.domain.aram;

import dfgg.domain.language.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 계산식 요소를 더한 값. 기본값의 하한·상한과 능력치 계수 목록이다.
 *
 * @param byLevel  레벨에 따라 바뀌는 요소가 하나라도 있는지. 있으면 레벨 문구를 붙인다
 * @param scalings 기본값에 더하지 않고 뒤에 괄호로 붙이는 능력치 계수
 */
public record CalculationValue(double low, double high, boolean byLevel, List<StatTerm> scalings) {

    public CalculationValue {
        scalings = List.copyOf(scalings);
    }

    static CalculationValue fixed(double value) {
        return new CalculationValue(value, value, false, List.of());
    }

    static CalculationValue byLevel(double start, double end) {
        return new CalculationValue(start, end, true, List.of());
    }

    static CalculationValue scaling(StatTerm term) {
        return new CalculationValue(0, 0, false, List.of(term));
    }

    CalculationValue plus(CalculationValue other) {
        List<StatTerm> merged = new ArrayList<>(scalings);
        merged.addAll(other.scalings);
        return new CalculationValue(low + other.low, high + other.high, byLevel || other.byLevel, merged);
    }

    /** 레벨에 따라 바뀌지 않고 계수도 없는 숫자 하나인지. */
    boolean isConstant() {
        return low == high && !byLevel && scalings.isEmpty();
    }

    /** 하한과 상한이 같으면 숫자 하나, 다르면 {@code 하한~상한}이다. 단위는 숫자마다 붙인다. */
    String baseText(double scale, String unit) {
        String low = DisplayNumber.of(this.low * scale) + unit;
        if (this.low == high) {
            return low;
        }
        return low + "~" + DisplayNumber.of(high * scale) + unit;
    }

    String scalingText(boolean percent, double multiplier, Language language) {
        return scalings.stream()
                .map(term -> term.text(percent, multiplier, language))
                .collect(Collectors.joining());
    }

    /** 능력치 계수 하나. 기본값 뒤에 {@code (+추가 체력의 4%)}처럼 붙는다. */
    public record StatTerm(ChampionStat stat, boolean bonus, double coefficient) {

        /** 백분율 계산식은 계수가 작아서({@code 0.00045}) 능력치 100당 값으로 쓴다. */
        String text(boolean percent, double multiplier, Language language) {
            String value = DisplayNumber.of(coefficient * multiplier * (percent ? 10_000 : 100)) + "%";
            String statName = bonusWord(language) + stat.nameIn(language);
            if (language == Language.KO_KR) {
                return percent ? " (+" + statName + " 100당 " + value + ")" : " (+" + statName + "의 " + value + ")";
            }
            return percent ? " (+" + value + " per 100 " + statName + ")" : " (+" + value + " " + statName + ")";
        }

        private String bonusWord(Language language) {
            if (!bonus) {
                return "";
            }
            return language == Language.KO_KR ? "추가 " : "bonus ";
        }
    }
}
