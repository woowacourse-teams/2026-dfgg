package dfgg.application.recommend;

/**
 * 다음 아이템 후보 하나와 그 점수. 안전 구역(Wilson 하한)과 탐색 구역(카운터 maxSim)이
 * 서로 다른 방식으로 점수를 매기지만, 둘 다 "아이템 하나 + 점수" 형태로 합쳐진다는 점은
 * 같아서 하나의 타입을 공유한다.
 */
public record RankedItemCandidate(
        Long itemId,
        double score
) {

}
