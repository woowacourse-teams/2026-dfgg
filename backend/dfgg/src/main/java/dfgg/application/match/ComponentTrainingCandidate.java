package dfgg.application.match;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dfgg.application.recommend.component.ComponentCandidateFeatures;

/** 학습 export 전용. label=null이면 비교 정답이 확정되지 않은 후보다. */
public record ComponentTrainingCandidate(@JsonUnwrapped ComponentCandidateFeatures candidate, Integer label) {
    public ComponentTrainingCandidate {
        if (candidate == null || label != null && label != 0 && label != 1) {
            throw new IllegalArgumentException("구매 후보와 0/1 또는 미확정 라벨이 필요합니다.");
        }
    }
}
