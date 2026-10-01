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

    private static final java.util.Set<Integer> NON_INVENTORY_MARKERS = java.util.Set.of(
            1200, 1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 2001, 2002);

    public static boolean isNonInventoryMarker(int itemId) {
        return NON_INVENTORY_MARKERS.contains(itemId);
    }

    private final Map<Integer, Integer> quantities;

    private final boolean bootsInQuestSlot;

    private final boolean controlWardsInQuestSlot;

    private ItemInventory(Map<Integer, Integer> quantities, boolean bootsInQuestSlot, boolean controlWardsInQuestSlot) {
        this.quantities = Map.copyOf(quantities);
        this.bootsInQuestSlot = bootsInQuestSlot;
        this.controlWardsInQuestSlot = controlWardsInQuestSlot;
    }

    public static ItemInventory empty() {
        return new ItemInventory(Map.of(), false, false);
    }

    /** 아이템 한 개를 획득한다. */
    public ItemInventory add(int itemId) {
        validateItemId(itemId);
        Map<Integer, Integer> changed = new HashMap<>(quantities);
        changed.merge(itemId, 1, Integer::sum);
        return new ItemInventory(changed, bootsInQuestSlot, controlWardsInQuestSlot);
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
        return Optional.of(new ItemInventory(changed, bootsInQuestSlot, controlWardsInQuestSlot));
    }

    /** Bot 퀘스트 완료 시 신발 한 개를 별도 슬롯에 유지한다. 아이템 수량·능력치는 유지한다. */
    public ItemInventory moveBootsToQuestSlot() {
        return bootsInQuestSlot ? this : new ItemInventory(quantities, true, controlWardsInQuestSlot);
    }

    public boolean bootsInQuestSlot() {
        return bootsInQuestSlot;
    }

    /** 16.18 서포터의 전용 슬롯은 제어 와드 두 개까지 보관한다. 수량은 함께 유지한다. */
    public ItemInventory moveControlWardsToQuestSlot() {
        return controlWardsInQuestSlot ? this : new ItemInventory(quantities, bootsInQuestSlot, true);
    }

    public boolean controlWardsInQuestSlot() {
        return controlWardsInQuestSlot;
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
        return bootsInQuestSlot == inventory.bootsInQuestSlot
                && controlWardsInQuestSlot == inventory.controlWardsInQuestSlot
                && quantities.equals(inventory.quantities);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(quantities, bootsInQuestSlot, controlWardsInQuestSlot);
    }
}
