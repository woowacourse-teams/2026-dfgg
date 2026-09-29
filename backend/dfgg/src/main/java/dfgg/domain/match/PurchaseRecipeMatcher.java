package dfgg.domain.match;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 구매 아이템의 패치별 조합식을 따라가며 실제 ITEM_DESTROYED 재료를 대응시킨다.
 * 직접 재료가 없으면 같은 조합 위치의 검증된 변형 재료를 찾고, 아니면 하위 레시피를 탐색한다.
 * 관측된 제거 이벤트를 각각 한 번만 사용하며, 남는 이벤트·순환 조합식·모호한 변형 후보는 불일치로 판단한다.
 * 조합식의 모든 재료가 제거 이벤트로 나타나야 한다는 뜻은 아니다. 이 객체는 관측된 제거가
 * 구매의 조합 경로에 모순 없이 들어맞는지만 검증하고, 비용 계산과 재료 이벤트 연결은 맡지 않는다.
 */
public class PurchaseRecipeMatcher {
    private final PurchaseItemCatalog catalog;

    public PurchaseRecipeMatcher(PurchaseItemCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("패치별 아이템 카탈로그가 필요합니다.");
        }
        this.catalog = catalog;
    }

    /** 모든 관측 제거 이벤트가 서로 다른 조합 위치에 배정되면 참을 반환한다. */
    public boolean matches(PurchaseCatalogItem purchased, PurchaseMaterials materials) {
        RemainingPurchaseMaterials remaining = materials.remaining();
        try {
            consumeIngredients(purchased, Set.of(purchased.id()), remaining);
        } catch (IllegalStateException exception) {
            return false;
        }
        return remaining.allConsumed();
    }

    /** 기대 재료 하나에 직접 ID, 동등한 변형 ID, 하위 레시피 순으로 관측 이벤트를 대응시킨다. */
    private void consume(int expectedId, int parentId, Set<Integer> path, RemainingPurchaseMaterials remaining) {
        if (expectedId <= 0 || path.contains(expectedId)) {
            throw new IllegalStateException("조합식의 ID 또는 순환이 잘못되었습니다.");
        }
        if (remaining.consume(expectedId)) {
            return;
        }
        Integer replacementId = replacementFor(expectedId, parentId, remaining);
        if (replacementId != null) {
            remaining.consume(replacementId);
            return;
        }
        PurchaseCatalogItem expected = catalog.find(expectedId)
                .orElseThrow(() -> new IllegalStateException("조합 재료가 카탈로그에 없습니다."));
        consumeIngredients(expected, extended(path, expectedId), remaining);
    }

    /** 남은 제거 이벤트에서 같은 조합 위치의 동등한 재료가 정확히 하나일 때만 선택한다. */
    private Integer replacementFor(int expectedId, int parentId, RemainingPurchaseMaterials remaining) {
        Optional<PurchaseCatalogItem> expected = catalog.find(expectedId);
        if (expected.isEmpty()) {
            return null;
        }
        Integer replacementId = null;
        for (int observedId : remaining.availableIds()) {
            Optional<PurchaseCatalogItem> observed = catalog.find(observedId);
            if (observed.isEmpty() || !expected.orElseThrow().canBeReplacedBy(observed.orElseThrow(), parentId)) {
                continue;
            }
            if (replacementId != null) {
                throw new IllegalStateException("대체 재료 후보가 여러 개입니다.");
            }
            replacementId = observedId;
        }
        return replacementId;
    }

    /** 현재 아이템의 from 목록을 따라 각 조합 위치를 재귀적으로 탐색한다. */
    private void consumeIngredients(PurchaseCatalogItem parent, Set<Integer> path, RemainingPurchaseMaterials remaining) {
        for (int childId : parent.ingredients()) {
            consume(childId, parent.id(), path, remaining);
        }
    }

    /** 현재 탐색 경로를 복사해 하위 레시피에서 같은 아이템을 다시 만나는 순환을 막는다. */
    private Set<Integer> extended(Set<Integer> path, int itemId) {
        Set<Integer> next = new HashSet<>(path);
        next.add(itemId);
        return next;
    }
}
