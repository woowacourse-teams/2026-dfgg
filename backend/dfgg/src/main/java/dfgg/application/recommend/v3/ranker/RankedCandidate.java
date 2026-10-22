package dfgg.application.recommend.v3.ranker;

import dfgg.application.recommend.v3.feature.FeatureVector;

/**
 * 순위가 매겨진 후보 하나.
 * <p>
 * itemId만 돌려주면 랭킹에 쓴 feature가 그대로 버려진다. 추천 근거를 응답에 담으려면
 * 그 값이 필요하고, 다시 계산하는 것은 낭비이자 서빙과 다른 값을 만들 위험이다.
 * <p>
 * {@code modelScore}는 raw margin이라 사용자에게 보여줄 값은 아니지만,
 * 순위가 왜 그렇게 나왔는지 관측하는 데 쓴다.
 */
public record RankedCandidate(Long itemId, double modelScore, FeatureVector features) {
}
