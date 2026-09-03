package dfgg.presentation.dto;

/**
 * 한 묶음이 이 아이템의 점수를 얼마나 올리고(양수) 내렸는지(음수).
 *
 * <p>모델의 raw margin 단위라 그 자체로 확률이 아니다. 크기끼리의 비교로 읽어야 한다.
 */
public record GroupContribution(String group, double value) {
}
