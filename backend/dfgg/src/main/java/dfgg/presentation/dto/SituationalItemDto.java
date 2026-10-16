package dfgg.presentation.dto;

import java.util.List;

/** 상대 조합 때문에 고려할 아이템. Core 목표와 무관하게 요청당 하나의 목록으로 낸다. */
public record SituationalItemDto(Long itemId, String name, String imageUrl, String effect,
                                 List<ChampionRefDto> reasonChampions, long additionalCost, boolean affordable) {
    public SituationalItemDto {
        if (itemId == null || itemId <= 0 || name == null || name.isBlank() || imageUrl == null || imageUrl.isBlank()
                || effect == null || reasonChampions == null || reasonChampions.isEmpty() || additionalCost < 0) {
            throw new IllegalArgumentException("상황 대응 아이템의 표시 정보·근거 챔피언·비용이 필요합니다.");
        }
        reasonChampions = List.copyOf(reasonChampions);
    }
}
