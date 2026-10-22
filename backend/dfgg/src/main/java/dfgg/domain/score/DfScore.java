package dfgg.domain.score;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 최종 점수. 0~100으로 자르고 소수 첫째 자리로 반올림해 보여준다.
 */
public class DfScore implements Comparable<DfScore> {

    private static final double MINIMUM = 0.0;
    private static final double MAXIMUM = 100.0;

    /** 반올림 전 값. MVP·ACE 동점 처리에 쓴다. */
    private final double exactValue;

    public DfScore(double value) {
        this.exactValue = Math.clamp(value, MINIMUM, MAXIMUM);
    }

    public double value() {
        return BigDecimal.valueOf(exactValue)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 반올림 전 값으로 비교한다. */
    @Override
    public int compareTo(DfScore other) {
        return Double.compare(exactValue, other.exactValue);
    }
}
