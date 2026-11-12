package dfgg.application.recommend.v3.feature;

/**
 * 후보 하나와 그 feature 벡터. 학습 데이터의 한 행이자 서빙 시 랭커의 입력 한 건이다.
 */
public record CandidateFeatures(Long itemId, FeatureVector vector) {
}
