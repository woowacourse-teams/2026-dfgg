package dfgg.domain.recommendation;

/**
 * 서로 다른 챔피언 태그 정책의 아이템 근거 점수를 공통 척도로 변환한다.
 */
final class BuildScoreNormalizer {

    private BuildScoreNormalizer() {
    }

    static double normalizeItemEvidence(
            int rawScore,
            int coreItemCount,
            int maximumScorePerItem
    ) {
        if (rawScore < 0) {
            throw new IllegalArgumentException("raw 점수는 0 이상이어야 합니다.");
        }
        if (coreItemCount < 1) {
            throw new IllegalArgumentException("코어 아이템 개수는 1 이상이어야 합니다.");
        }
        if (maximumScorePerItem < 1) {
            throw new IllegalArgumentException("아이템당 최대 점수는 1 이상이어야 합니다.");
        }
        return (double) rawScore / (coreItemCount * maximumScorePerItem);
    }
}
