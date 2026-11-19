package dfgg.domain.match;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 상대 조합 때문에 효과가 필요한 재료. Component LTR 순위와 별개로 추천한다.
 * 신규 패치에서는 이 목록과 조회표를 함께 검토해야 한다.
 */
public final class SituationalComponentPolicy {
    // 조회표에 담는 하한. 실제 적용 기준은 설정(SituationalThresholds)으로 이보다 높게 조정한다.
    public static final int MIN_SUPPORT = 30;
    public static final int MIN_GAMES = 50;
    public static final double LIFT_FLOOR = 1.5;
    public static final double BASE_RATE_FLOOR = 0.01;

    public enum Effect { GRIEVOUS_WOUNDS, MAGIC_RESIST }

    public static final Map<Integer, Effect> ITEMS = Map.of(
            3916, Effect.GRIEVOUS_WOUNDS,   // 망각의 구
            3123, Effect.GRIEVOUS_WOUNDS,   // 처형인의 대검
            3076, Effect.GRIEVOUS_WOUNDS,   // 덤불 조끼
            1033, Effect.MAGIC_RESIST);     // 마법무효화의 망토

    private SituationalComponentPolicy() {}

    public static Set<Integer> items() {
        return ITEMS.keySet();
    }

    public static Effect effectOf(int itemId) {
        Effect effect = ITEMS.get(itemId);
        if (effect == null) throw new IllegalArgumentException("Situational 화이트리스트 아이템이 아닙니다: " + itemId);
        return effect;
    }

    /** 같은 효과의 재료나 그 재료로 만든 아이템을 이미 보유하면 효과가 확보된 것으로 본다. */
    public static boolean effectAlreadyOwned(int itemId, Collection<Integer> ownedItemIds, PurchaseItemCatalog catalog) {
        Effect effect = effectOf(itemId);
        Set<Integer> sameEffect = ITEMS.entrySet().stream().filter(entry -> entry.getValue() == effect)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        return ownedItemIds.stream().anyMatch(owned -> sameEffect.contains(owned) || containsInRecipe(owned, sameEffect, catalog));
    }

    private static boolean containsInRecipe(int owned, Set<Integer> ingredients, PurchaseItemCatalog catalog) {
        var item = catalog.find(owned).orElse(null);
        if (item == null) return false;
        for (int ingredient : item.ingredients()) {
            if (ingredients.contains(ingredient) || containsInRecipe(ingredient, ingredients, catalog)) return true;
        }
        return false;
    }
}
