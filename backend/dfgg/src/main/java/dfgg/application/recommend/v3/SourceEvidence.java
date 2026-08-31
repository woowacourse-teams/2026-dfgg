package dfgg.application.recommend.v3;

/**
 * 한 generator가 특정 아이템에 대해 남긴 근거. 이 score와 rank는 그대로 LTR feature가 된다.
 *
 * <p>이 값이 존재한다는 것 자체가 "그 generator가 이 아이템을 후보로 냈다"는 뜻이다.
 * 발견하지 못한 경우는 score 0.0이 아니라 {@link ItemCandidate}에서 아예 부재로 표현한다 —
 * "0점으로 평가했다"와 "평가 대상에 없었다"는 다른 정보다.
 */
public record SourceEvidence(double score, int rank) {
}
