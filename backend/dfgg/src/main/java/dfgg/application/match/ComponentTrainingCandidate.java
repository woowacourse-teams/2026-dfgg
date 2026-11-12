package dfgg.application.match;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dfgg.application.recommend.component.ComponentCandidateFeatures;

/**
 * 학습 export 전용. label=1은 관측 구매, label=0은 이번 관측에서 선택되지 않은 대안이다.
 * 구매의 최적성이나 품질을 나타내지 않으며, label=null은 관측 구매 대조가 확정되지 않은 후보다.
 */
public record ComponentTrainingCandidate(@JsonUnwrapped ComponentCandidateFeatures candidate, Integer label,
        dfgg.domain.match.ComponentCandidateGenerator.Source candidateSource,
        Integer policyItemId) {
    public ComponentTrainingCandidate(ComponentCandidateFeatures candidate, Integer label) {
        this(candidate, label, dfgg.domain.match.ComponentCandidateGenerator.Source.TARGET_PATH, null);
    }
    public ComponentTrainingCandidate {
        if (candidate == null || label != null && label != 0 && label != 1) {
            throw new IllegalArgumentException("구매 후보와 0/1 또는 미확정 라벨이 필요합니다.");
        }
    }
}
