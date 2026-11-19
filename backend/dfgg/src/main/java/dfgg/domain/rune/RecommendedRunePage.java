package dfgg.domain.rune;

/**
 * 추천한 룬 페이지 하나.
 *
 * @param pathGames    이 페이지와 같은 (핵심 룬, 보조 계열) 묶음을 쓴 판 수
 * @param pathPickRate 전체 판 중 그 묶음을 쓴 비율
 */
public record RecommendedRunePage(int rank, RunePage page, int pathGames, double pathPickRate) {
}
