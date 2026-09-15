package dfgg.presentation.dto;

import dfgg.domain.item.Item;

/**
 * v3 추천 응답의 아이템 하나.
 * description에는 상대하기 좋은 적군 챔피언 목록, 시너지가 좋은 아군 챔피언 목록, 아이템 특성을 담는다.
 * <p>
 * 순위가 왜 이렇게 매겨졌는지(SHAP 기여도)는 싣지 않는다. 서버 DEBUG 로그로만 남는다.
 */
public record RecommendedItemDto(
        Long id,
        String name,
        RecommendationDescription description
) {
    public static RecommendedItemDto of(Item item, RecommendationDescription description) {
        return new RecommendedItemDto(item.getItemId(), item.getName(), description);
    }
}
