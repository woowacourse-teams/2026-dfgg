package dfgg.domain.rune;

import java.util.List;

/**
 * 한 챔피언·포지션의 룬 추천 결과.
 *
 * @param games 추천에 쓴 전체 판 수. 판 수가 모자라 페이지가 비어 있어도 함께 내려준다
 */
public record RuneRecommendation(int games, List<RecommendedRunePage> pages) {

    public RuneRecommendation {
        pages = List.copyOf(pages);
    }
}
