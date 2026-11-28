package dfgg.domain.rune;

/**
 * 룬 추천 기준.
 *
 * @param minimumGames     챔피언·포지션 전체 판 수가 이보다 적으면 추천하지 않는다
 * @param minimumPathGames (핵심 룬, 보조 계열) 묶음 판 수가 이보다 적으면 그 묶음은 고르지 않는다
 * @param pageCount        내려줄 페이지 수
 */
public record RuneRecommendationPolicy(int minimumGames, int minimumPathGames, int pageCount) {

    public RuneRecommendationPolicy {
        if (minimumGames < 1 || minimumPathGames < 1 || pageCount < 1) {
            throw new IllegalArgumentException("룬 추천 기준 판 수와 페이지 수는 1 이상이어야 합니다.");
        }
    }
}
