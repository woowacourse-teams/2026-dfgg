package dfgg.application.recommend.v3.ranker;

/**
 * 한 후보의 feature별 기여도와 기준값.
 *
 * <p>{@code baseValue + sum(values) == model.predict(features)}가 성립한다(SHAP의 efficiency).
 * 이 항등식이 깨지면 "이 아이템이 추천된 이유"라고 부를 근거가 없어진다.
 */
public record FeatureContributions(double[] values, double baseValue) {
}
