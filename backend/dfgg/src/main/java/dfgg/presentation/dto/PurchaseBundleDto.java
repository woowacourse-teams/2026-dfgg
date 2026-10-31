package dfgg.presentation.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 이번에 구매할 묶음 하나. purchaseOrder는 선호 순위가 아니라 실행 가능한 순서다. */
public record PurchaseBundleDto(List<PurchaseItemDto> items, long additionalCost, List<Long> purchaseOrder) {
    public PurchaseBundleDto {
        if (items == null || items.isEmpty() || purchaseOrder == null || additionalCost < 0) {
            throw new IllegalArgumentException("구매 목록·순서와 0 이상의 추가 비용이 필요합니다.");
        }
        items = List.copyOf(items);
        purchaseOrder = List.copyOf(purchaseOrder);
        Map<Long, Long> quantities = new HashMap<>();
        for (PurchaseItemDto item : items) {
            if (quantities.putIfAbsent(item.itemId(), (long) item.quantity()) != null) {
                throw new IllegalArgumentException("구매 아이템 ID는 수량으로 합쳐야 합니다.");
            }
        }
        Map<Long, Long> orderedQuantities = new HashMap<>();
        purchaseOrder.forEach(id -> orderedQuantities.merge(id, 1L, Long::sum));
        if (!quantities.equals(orderedQuantities)) {
            throw new IllegalArgumentException("구매 순서와 아이템별 수량이 일치해야 합니다.");
        }
    }
}
