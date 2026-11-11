package dfgg.domain.aram;

import dfgg.domain.language.Language;
import java.util.Map;
import java.util.Optional;

/** 증강 주문들의 값표. 설명 원문의 자리표시자 이름으로 데이터값이나 계산식을 찾아 화면에 쓸 표기로 만든다. */
public final class SpellValues {

    private final Map<String, Double> dataValues;
    private final Map<String, SpellCalculation> calculations;

    public SpellValues(Map<String, Double> dataValues) {
        this(dataValues, Map.of());
    }

    public SpellValues(Map<String, Double> dataValues, Map<String, SpellCalculation> calculations) {
        this.dataValues = Map.copyOf(dataValues);
        this.calculations = Map.copyOf(calculations);
    }

    /**
     * 같은 이름이 둘 다 있으면 데이터값을 쓴다.
     *
     * @param percentFollows 원문에서 자리표시자 바로 뒤에 {@code %}가 있었는지. 있으면 값 바로 뒤에 붙인다
     */
    public Optional<String> format(String name, double multiplier, boolean percentFollows, Language language) {
        Double dataValue = dataValues.get(name);
        if (dataValue != null) {
            return Optional.of(DisplayNumber.of(dataValue * multiplier) + (percentFollows ? "%" : ""));
        }
        return Optional.ofNullable(calculations.get(name))
                .flatMap(calculation -> calculation.format(dataValues, multiplier, percentFollows, language));
    }
}
