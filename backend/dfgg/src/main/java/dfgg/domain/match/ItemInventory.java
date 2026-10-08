package dfgg.domain.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한 참가자의 아이템별 보유 수량이다. 같은 재료를 여러 개 보유할 수 있으며,
 * 모든 변경은 새 Inventory를 반환하므로 과거 구매 시점의 스냅샷이 바뀌지 않는다.
 */
public final class ItemInventory {

    private final Map<Integer, Integer> quantities;

    private ItemInventory(Map<Integer, Integer> quantities) {
        this.quantities = Map.copyOf(quantities);
    }

    public static ItemInventory empty() {
        return new ItemInventory(Map.of());
    }

    /** 아이템 한 개를 획득한다. */
    public ItemInventory add(int itemId) {
        validateItemId(itemId);
        Map<Integer, Integer> changed = new HashMap<>(quantities);
        changed.merge(itemId, 1, Integer::sum);
        return new ItemInventory(changed);
    }

    /** 구매 직전에 조합으로 제거된 재료들을 스냅샷에 되돌린다. */
    public ItemInventory restore(List<Integer> itemIds) {
        ItemInventory restored = this;
        for (int itemId : itemIds) {
            restored = restored.add(itemId);
        }
        return restored;
    }

    /** 보유한 아이템 한 개를 제거한다. 원천 로그와 수량이 모순되면 빈 결과를 반환한다. */
    public Optional<ItemInventory> remove(int itemId) {
        validateItemId(itemId);
        int quantity = quantityOf(itemId);
        if (quantity == 0) {
            return Optional.empty();
        }
        Map<Integer, Integer> changed = new HashMap<>(quantities);
        if (quantity == 1) {
            changed.remove(itemId);
        } else {
            changed.put(itemId, quantity - 1);
        }
        return Optional.of(new ItemInventory(changed));
    }

    public int quantityOf(int itemId) {
        validateItemId(itemId);
        return quantities.getOrDefault(itemId, 0);
    }

    public Map<Integer, Integer> quantities() {
        return quantities;
    }

    private void validateItemId(int itemId) {
        if (itemId <= 0) {
            throw new IllegalArgumentException("아이템 ID는 양수여야 합니다.");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemInventory inventory)) {
            return false;
        }
        return quantities.equals(inventory.quantities);
    }

    @Override
    public int hashCode() {
        return quantities.hashCode();
    }
}
