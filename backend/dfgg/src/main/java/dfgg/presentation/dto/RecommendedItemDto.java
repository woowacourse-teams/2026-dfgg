package dfgg.presentation.dto;

import dfgg.domain.item.Item;

/**
 * v3 추천 응답의 아이템 하나.
 * description에는 상대하기 좋은 적군 챔피언 목록, 시너지가 좋은 아군 챔피언 목록, 아이템 특성을 담는다.
 */
public record RecommendedItemDto(
        Long id,
        String name,
        RecommendationDescription description,
        RecommendationReasons reasons
) {
    public static RecommendedItemDto of(
            Item item, RecommendationDescription description, RecommendationReasons reasons) {
        return new RecommendedItemDto(item.getItemId(), item.getName(), description, reasons);
    }
}
