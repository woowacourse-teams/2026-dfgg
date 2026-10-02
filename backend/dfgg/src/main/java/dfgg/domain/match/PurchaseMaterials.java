package dfgg.domain.match;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 한 번의 구매에 연결된 ITEM_DESTROYED 이벤트의 아이템 ID를 원천 수량 그대로 보존한다.
 * 같은 ID가 두 번 제거되면 두 항목을 유지해 가격 합산과 레시피 대조에서 각각 한 번씩 처리한다.
 * 재료가 없다는 빈 목록과 재료 연결 자체에 실패한 경우는 구분해야 하며, 연결 실패는 이 객체를 만들기 전에 처리한다.
 */
public record PurchaseMaterials(List<Integer> ids) {

    /** null이나 잘못된 아이템 ID를 거부하고 원본 목록의 변경으로부터 값을 보호한다. */
    public PurchaseMaterials {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("제거 재료는 양의 아이템 ID 목록이어야 합니다.");
        }
        ids = List.copyOf(ids);
    }

    /** 각 제거 이벤트에 해당하는 아이템의 총가격을 합산하며, 조회 실패나 정수 범위 초과 시 빈 값을 반환한다. */
    public OptionalInt totalPrice(PurchaseItemCatalog catalog) {
        long total = 0;
        for (int id : ids) {
            Optional<PurchaseCatalogItem> item = catalog.find(id);
            if (item.isEmpty()) {
                return OptionalInt.empty();
            }
            total += item.orElseThrow().totalPrice();
        }
        if (total > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) total);
    }

    /** 레시피 대조 중 재료를 소모해도 원본 목록이 바뀌지 않도록 별도의 잔여 수량을 만든다. */
    RemainingPurchaseMaterials remaining() {
        return new RemainingPurchaseMaterials(ids);
    }
}
