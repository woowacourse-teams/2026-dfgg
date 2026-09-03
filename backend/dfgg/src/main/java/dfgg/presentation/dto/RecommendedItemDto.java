package dfgg.presentation.dto;

import dfgg.domain.item.Item;

/**
 * v3 추천 응답의 아이템 하나.
 * <p>
 * {@code description}이 사용자에게 보여줄 이유이고, {@code reasons}는 그 문장을 만든 내부 근거다.
 * SHAP 값은 raw margin 단위라 그 자체로는 읽을 값이 아니다 — E8에서 응답에서 빼고 로그로 옮긴다.
 */
public record RecommendedItemDto(
        Long id,
        String name,
        String description,
        RecommendationReasons reasons
) {
    public static RecommendedItemDto of(Item item, String description, RecommendationReasons reasons) {
        return new RecommendedItemDto(item.getItemId(), item.getName(), description, reasons);
    }
}
