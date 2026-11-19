package dfgg.domain.aram;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 설명에 쓰는 숫자 표기. 게임 데이터는 float이라 {@code 0.2}가 {@code 0.20000000298}로 들어 있어 소수 첫째 자리까지만 쓴다. */
final class DisplayNumber {

    private DisplayNumber() {
    }

    static String of(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
