package dfgg.domain.aram;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/** 증강 주문들의 값표. 설명 원문의 자리표시자 이름으로 값을 찾아 화면에 쓸 표기로 만든다. */
public final class SpellValues {

    private final Map<String, Double> dataValues;

    public SpellValues(Map<String, Double> dataValues) {
        this.dataValues = Map.copyOf(dataValues);
    }

    /** @param percentFollows 원문에서 자리표시자 바로 뒤에 {@code %}가 있었는지. 있으면 값 바로 뒤에 붙인다 */
    public Optional<String> format(String name, double multiplier, boolean percentFollows) {
        return Optional.ofNullable(dataValues.get(name))
                .map(value -> displayed(value * multiplier) + (percentFollows ? "%" : ""));
    }

    /** 게임 데이터는 float이라 {@code 0.2}가 {@code 0.20000000298}로 들어 있다. 소수 첫째 자리까지만 쓴다. */
    private static String displayed(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
