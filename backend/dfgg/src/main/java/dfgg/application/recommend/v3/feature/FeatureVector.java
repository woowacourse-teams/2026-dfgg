package dfgg.application.recommend.v3.feature;

import java.util.Arrays;

/**
 * 후보 하나의 feature 값. 길이는 항상 {@link FeatureName} 개수와 같고 인덱스가 대응한다.
 * <p>
 * 초기값이 {@code NaN}인 것이 핵심이다. "값이 0"과 "데이터가 없다"는 다른 정보인데,
 * 결측을 0으로 채우면 둘이 뒤섞인다 — counter가 0점으로 평가한 아이템과 counter가 아예
 * 보지 않은 아이템을 모델이 구분할 수 없게 된다.
 * LightGBM은 NaN을 별도 분기로 다룬다.
 */
public final class FeatureVector {

    private final double[] values;

    private FeatureVector(double[] values) {
        this.values = values;
    }

    public static FeatureVector empty() {
        double[] values = new double[FeatureName.values().length];
        Arrays.fill(values, Double.NaN);
        return new FeatureVector(values);
    }

    /** 질의 단위 feature를 후보마다 다시 계산하지 않기 위한 복사본. */
    public static FeatureVector copyOf(FeatureVector other) {
        return new FeatureVector(other.values.clone());
    }

    public void set(FeatureName name, double value) {
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException(
                    "feature 값은 유한해야 합니다: " + name.exportName() + "=" + value);
        }
        values[name.index()] = value;
    }

    public double get(FeatureName name) {
        return values[name.index()];
    }

    public double[] values() {
        return values.clone();
    }
}
