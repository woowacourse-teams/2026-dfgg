package dfgg.presentation.dto;

import dfgg.domain.item.Item;

/**
 * v3 추천 응답의 아이템 하나. 아이템 정보에 추천 근거가 붙는다.
 *
 * <p>v1/v2가 쓰는 {@link ItemDto}와 일부러 분리했다. 거기에 필드를 더하면 v3와 무관한
 * 엔드포인트의 응답까지 바뀐다.
 */
public record RecommendedItemDto(
        Long id,
        String name,
        RecommendationReasons reasons
) {
    public static RecommendedItemDto of(Item item, RecommendationReasons reasons) {
        return new RecommendedItemDto(item.getItemId(), item.getName(), reasons);
    }
}
