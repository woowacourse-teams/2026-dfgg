package dfgg.domain.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 Data Dragon 빌드의 아이템 정의에서 구매 비용 계산에 필요한 정보만 보존한다.
 * {@code ingredients}는 해당 아이템의 조합 재료(from), {@code upgrades}는 이 아이템을 재료로 쓰는 상위 아이템(into)이다.
 * 서비스의 영속 {@code Item}과 달리 매치의 과거 패치 카탈로그를 표현하며 DB에 저장하지 않는다.
 * 재료 수량과 상위 조합 관계를 이용해 원천 이벤트에 나타난 변형 재료의 동등성을 판단한다.
 */
public record PurchaseCatalogItem(
        int id,
        int totalPrice,
        boolean purchasable,
        List<Integer> ingredients,
        List<Integer> upgrades
) {

    /** 유효한 ID·가격·조합 관계만 보관하고 전달받은 목록을 복사한다. */
    public PurchaseCatalogItem {
        if (id <= 0 || totalPrice < 0 || ingredients == null || upgrades == null) {
            throw new IllegalArgumentException("아이템 ID, 가격, 조합 관계가 유효해야 합니다.");
        }
        if (ingredients.stream().anyMatch(value -> value == null || value <= 0)
                || upgrades.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException("조합 아이템 ID는 양수여야 합니다.");
        }
        ingredients = List.copyOf(ingredients);
        upgrades = List.copyOf(upgrades);
    }

    /** 이 아이템의 into 목록에 대상 완성품이 있는지 확인한다. */
    public boolean buildsInto(int parentId) {
        return upgrades.contains(parentId);
    }

    /**
     * 카탈로그의 표준 재료와 이벤트에서 제거된 변형 재료가 같은 조합 위치를 차지할 수 있는지 확인한다.
     * 두 아이템의 ID는 달라야 하고, 비어 있지 않은 from 재료의 종류와 수량, 양수 총가격,
     * 구매 가능 여부, 대상 완성품으로 이어지는 into 관계가 모두 같아야 한다.
     */
    public boolean canBeReplacedBy(PurchaseCatalogItem observed, int parentId) {
        if (observed == null || id == observed.id()) {
            return false;
        }
        if (ingredients.isEmpty() || !ingredientCounts().equals(observed.ingredientCounts())) {
            return false;
        }
        if (!purchasable || !observed.purchasable()) {
            return false;
        }
        if (!buildsInto(parentId) || !observed.buildsInto(parentId)) {
            return false;
        }
        return totalPrice > 0 && totalPrice == observed.totalPrice();
    }

    /** from 목록의 순서는 무시하되 동일 재료가 여러 개 필요한 조합식의 수량은 유지한다. */
    private Map<Integer, Integer> ingredientCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        ingredients.forEach(ingredient -> counts.merge(ingredient, 1, Integer::sum));
        return counts;
    }
}
