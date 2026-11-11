package dfgg.domain.aram;

/** 계산식 기본값의 하한과 상한. 레벨에 따라 바뀌는 요소가 하나라도 있으면 레벨 문구를 붙인다. */
public record CalculationRange(double low, double high, boolean byLevel) {

    static CalculationRange fixed(double value) {
        return new CalculationRange(value, value, false);
    }

    static CalculationRange byLevel(double start, double end) {
        return new CalculationRange(start, end, true);
    }

    CalculationRange plus(CalculationRange other) {
        return new CalculationRange(low + other.low, high + other.high, byLevel || other.byLevel);
    }

    /** 하한과 상한이 같으면 숫자 하나, 다르면 {@code 하한~상한}이다. 단위는 숫자마다 붙인다. */
    String text(double scale, String unit) {
        String low = DisplayNumber.of(this.low * scale) + unit;
        if (this.low == high) {
            return low;
        }
        return low + "~" + DisplayNumber.of(high * scale) + unit;
    }
}
