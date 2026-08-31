package dfgg.application.recommend.v3;

/**
 * generator가 매긴 아이템 하나와 그 점수.
 *
 * <p>rank를 담지 않는다 — rank는 {@link GeneratorResult}가 목록 순서에서 파생한다.
 * generator가 score와 rank를 따로 넘기면 둘이 어긋날 수 있고, 그 불일치는 조용히
 * LTR feature까지 흘러간다.
 */
public record ScoredItem(Long itemId, double score) {
}
