package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.feature.ReasonGroup;

/** 한 묶음이 점수를 얼마나 움직였는지. 문장으로 옮기기 전 단계의 값이다. */
public record GroupWeight(ReasonGroup group, double value) {
}
