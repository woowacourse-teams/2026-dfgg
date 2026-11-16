package dfgg.application.recommend.v3;

/**
 * 추천을 받는 시점의 내 상태. 챔피언·포지션·산 Core는 {@link RecommendationQuery}에 이미 있어 여기에 싣지 않는다.
 */
public record MyState(int level, int kills, int deaths, int assists) {
}
