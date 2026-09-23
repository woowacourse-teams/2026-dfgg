package dfgg.presentation.dto;

import dfgg.domain.champion.Champion;

/**
 * 챔피언 목록의 한 명. 검색창·밴픽 화면이 쓴다.
 *
 * @param riotKey 영문 키. 요청의 챔피언 이름으로도 받는다 (예: {@code MonkeyKing})
 * @param name    한글 이름
 */
public record ChampionSummaryDto(Long id, String riotKey, String name, String imageUrl) {

    public static ChampionSummaryDto of(Champion champion, String imageUrl) {
        return new ChampionSummaryDto(
                champion.getChampionId(),
                champion.getRiotKey(),
                champion.getName().get("ko-KR"),
                imageUrl
        );
    }
}
