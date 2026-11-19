package dfgg.presentation.dto.response;

import dfgg.presentation.dto.RecommendedRunePageDto;
import java.util.List;

/**
 * @param patch         추천에 쓴 패치. 룬 기록이 하나도 없으면 비어 있다
 * @param championGames 이 패치에서 이 챔피언·포지션의 전체 판 수
 * @param pages         추천 페이지, 순위 순. 판 수가 기준보다 적으면 비어 있다
 */
public record RuneRecommendationResponse(String patch, int championGames, List<RecommendedRunePageDto> pages) {

    public RuneRecommendationResponse {
        pages = List.copyOf(pages);
    }

    public static RuneRecommendationResponse withoutPatch() {
        return new RuneRecommendationResponse(null, 0, List.of());
    }
}
