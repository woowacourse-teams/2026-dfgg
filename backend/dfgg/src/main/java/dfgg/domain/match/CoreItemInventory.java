package dfgg.domain.match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 한 선수가 들고 있는 Core와 각 Core를 얻은 시각. 아이템 기록을 받으면 새 보유를 돌려준다.
 *
 * <p>얻은 시각은 판 뒤에도 지우지 않는다. 다시 산 기록에 시각이 없으면 예전 시각이 남는다.
 * Core 구매 순서 계산기가 처음부터 이렇게 동작해 왔고, 이 규칙을 꺼내면서 바꾸지 않았다.
 */
public final class CoreItemInventory {

    private final List<Integer> itemIds;
    private final Map<Integer, Long> acquiredAt;

    private CoreItemInventory(List<Integer> itemIds, Map<Integer, Long> acquiredAt) {
        this.itemIds = List.copyOf(itemIds);
        this.acquiredAt = Map.copyOf(acquiredAt);
    }

    public static CoreItemInventory empty() {
        return new CoreItemInventory(List.of(), Map.of());
    }

    /** 빠진 아이템을 먼저 빼고 더해진 아이템을 넣는다. 되돌리기가 이 순서로 처리돼 왔다. */
    public CoreItemInventory changed(CoreItemChange change) {
        return removed(change.removedItemId()).added(change.addedItemId(), change.timestamp());
    }

    /**
     * 구매 기록 없이 얻은 아이템을 그 시각 자리에 끼워 넣는다. 그 시각보다 늦게 얻은 첫 아이템 앞이다.
     * 얻은 시각을 모르는 아이템은 비교에서 건너뛴다.
     */
    public CoreItemInventory acquiredWithoutEvent(int itemId, long timestamp) {
        List<Integer> nextItemIds = new ArrayList<>(itemIds);
        nextItemIds.add(insertIndexFor(timestamp), itemId);
        Map<Integer, Long> nextAcquiredAt = new HashMap<>(acquiredAt);
        nextAcquiredAt.put(itemId, timestamp);
        return new CoreItemInventory(nextItemIds, nextAcquiredAt);
    }

    public boolean owns(int itemId) {
        return itemIds.contains(itemId);
    }

    public boolean ownsAll(Collection<Integer> itemIds) {
        return this.itemIds.containsAll(itemIds);
    }

    /** 그 Core를 마지막으로 얻은 시각. 한 번도 시각과 함께 얻은 적이 없으면 빈 결과다. */
    public Optional<Long> acquiredAt(int itemId) {
        return Optional.ofNullable(acquiredAt.get(itemId));
    }

    /** 들고 있는 Core를 얻은 순서대로 돌려준다. */
    public List<Integer> itemIds() {
        return itemIds;
    }

    private CoreItemInventory removed(Integer itemId) {
        if (itemId == null || !itemIds.contains(itemId)) {
            return this;
        }
        List<Integer> nextItemIds = new ArrayList<>(itemIds);
        nextItemIds.remove(itemId);
        return new CoreItemInventory(nextItemIds, acquiredAt);
    }

    /** 이미 들고 있으면 아무것도 바꾸지 않는다. 얻은 시각도 그대로다. */
    private CoreItemInventory added(Integer itemId, Long timestamp) {
        if (itemId == null || itemIds.contains(itemId)) {
            return this;
        }
        List<Integer> nextItemIds = new ArrayList<>(itemIds);
        nextItemIds.add(itemId);
        Map<Integer, Long> nextAcquiredAt = new HashMap<>(acquiredAt);
        if (timestamp != null) {
            nextAcquiredAt.put(itemId, timestamp);
        }
        return new CoreItemInventory(nextItemIds, nextAcquiredAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CoreItemInventory inventory)) {
            return false;
        }
        return itemIds.equals(inventory.itemIds) && acquiredAt.equals(inventory.acquiredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemIds, acquiredAt);
    }

    private int insertIndexFor(long timestamp) {
        for (int index = 0; index < itemIds.size(); index++) {
            Long itemAcquiredAt = acquiredAt.get(itemIds.get(index));
            if (itemAcquiredAt != null && itemAcquiredAt > timestamp) {
                return index;
            }
        }
        return itemIds.size();
    }
}
