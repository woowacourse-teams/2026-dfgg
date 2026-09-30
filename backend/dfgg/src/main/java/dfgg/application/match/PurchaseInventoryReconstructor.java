package dfgg.application.match;

import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.ParticipantItemEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Raw의 전체 아이템 행동을 재생해 각 구매 거래 직전의 보유 수량을 복원한다. */
@Component
public class PurchaseInventoryReconstructor {

    private final PurchaseMaterialLinker materialLinker;

    public PurchaseInventoryReconstructor(PurchaseMaterialLinker materialLinker) {
        this.materialLinker = materialLinker;
    }

    /**
     * 구매 원천 이벤트를 키로 구매 직전 Inventory를 반환한다. 원천에서 얻지 못한 획득,
     * 미지원 아이템 행동, 모호한 조합 재료 연결이 있으면 해당 참가자의 구매를 결과에서 제외한다.
     */
    public Map<ParticipantItemEvent, ItemInventory> beforePurchases(List<ParticipantItemEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("전체 아이템 이벤트가 필요합니다.");
        }
        Map<Integer, List<ParticipantItemEvent>> byParticipant = groupByParticipant(events);
        Map<ParticipantItemEvent, ItemInventory> snapshots = new HashMap<>();
        for (List<ParticipantItemEvent> participantEvents : byParticipant.values()) {
            replay(participantEvents, snapshots);
        }
        return Map.copyOf(snapshots);
    }

    /** 참가자별 eventOrder를 유지하고 다른 참가자의 Inventory와 섞이지 않게 한다. */
    private Map<Integer, List<ParticipantItemEvent>> groupByParticipant(List<ParticipantItemEvent> events) {
        Map<Integer, List<ParticipantItemEvent>> grouped = new HashMap<>();
        for (ParticipantItemEvent event : events) {
            if (event == null) {
                throw new IllegalArgumentException("아이템 이벤트 목록에 null이 있습니다.");
            }
            grouped.computeIfAbsent(event.participantId(), ignored -> new ArrayList<>()).add(event);
        }
        return grouped;
    }

    /** 판매·제거·취소까지 적용하며, 불확실해진 참가자의 이후 스냅샷은 만들지 않는다. */
    private void replay(List<ParticipantItemEvent> events, Map<ParticipantItemEvent, ItemInventory> snapshots) {
        ItemInventory inventory = ItemInventory.empty();
        boolean reliable = true;
        int previousOrder = 0;
        String matchId = events.getFirst().matchId();
        for (ParticipantItemEvent event : events) {
            validateOrder(event, matchId, previousOrder);
            previousOrder = event.eventOrder();
            if (!reliable) {
                continue;
            }
            if ("ITEM_PURCHASED".equals(event.eventType())) {
                Optional<ItemInventory> before = beforePurchase(event, events, inventory);
                before.ifPresent(snapshot -> snapshots.put(event, snapshot));
            }
            Optional<ItemInventory> after = apply(event, inventory);
            if (after.isEmpty()) {
                reliable = false;
                continue;
            }
            inventory = after.orElseThrow();
        }
    }

    private void validateOrder(ParticipantItemEvent event, String matchId, int previousOrder) {
        if (!matchId.equals(event.matchId()) || event.eventOrder() != previousOrder + 1) {
            throw new IllegalArgumentException("한 경기의 참가자별 전체 아이템 이벤트를 eventOrder 순으로 전달해야 합니다.");
        }
    }

    /** 구매 직전 동일 거래에서 먼저 기록된 재료 제거를 스냅샷에만 되돌린다. */
    private Optional<ItemInventory> beforePurchase(ParticipantItemEvent purchase,
                                                   List<ParticipantItemEvent> events, ItemInventory inventory) {
        Optional<List<Integer>> materials = materialLinker.link(purchase, events);
        if (materials.isEmpty()) {
            if (hasUnlinkedDestroy(purchase, events)) {
                return Optional.empty();
            }
            return Optional.of(inventory);
        }
        return Optional.of(inventory.restore(materials.orElseThrow()));
    }

    /** 재료 제거가 같은 거래에 있을 수 있는데 연결하지 못했다면 직전 상태를 추측하지 않는다. */
    private boolean hasUnlinkedDestroy(ParticipantItemEvent purchase, List<ParticipantItemEvent> events) {
        for (int index = purchase.eventOrder() - 2; index >= 0; index--) {
            ParticipantItemEvent previous = events.get(index);
            if (previous.sourceFrameIndex() != purchase.sourceFrameIndex()
                    || !Objects.equals(previous.gameTimeMs(), purchase.gameTimeMs())
                    || "ITEM_PURCHASED".equals(previous.eventType())) {
                return false;
            }
            if ("ITEM_DESTROYED".equals(previous.eventType())) {
                return true;
            }
        }
        return false;
    }

    /** 원천 행동 하나가 보유 수량에 미치는 영향을 적용한다. */
    private Optional<ItemInventory> apply(ParticipantItemEvent event, ItemInventory inventory) {
        return switch (event.eventType()) {
            case "ITEM_PURCHASED" -> add(event.itemId(), inventory);
            case "ITEM_SOLD", "ITEM_DESTROYED" -> remove(event.itemId(), inventory);
            case "ITEM_UNDO" -> undo(event, inventory);
            default -> Optional.empty();
        };
    }

    private Optional<ItemInventory> add(Integer itemId, ItemInventory inventory) {
        if (itemId == null || itemId <= 0) {
            return Optional.empty();
        }
        return Optional.of(inventory.add(itemId));
    }

    private Optional<ItemInventory> remove(Integer itemId, ItemInventory inventory) {
        if (itemId == null || itemId <= 0) {
            return Optional.empty();
        }
        return inventory.remove(itemId);
    }

    /** UNDO는 beforeId를 빼고 afterId를 더한다. 0은 빈 슬롯을 뜻한다. */
    private Optional<ItemInventory> undo(ParticipantItemEvent event, ItemInventory inventory) {
        Integer beforeId = event.beforeItemId();
        Integer afterId = event.afterItemId();
        if (beforeId == null || afterId == null || beforeId < 0 || afterId < 0) {
            return Optional.empty();
        }
        ItemInventory changed = inventory;
        if (beforeId > 0) {
            Optional<ItemInventory> removed = changed.remove(beforeId);
            if (removed.isEmpty()) {
                return Optional.empty();
            }
            changed = removed.orElseThrow();
        }
        if (afterId > 0) {
            changed = changed.add(afterId);
        }
        return Optional.of(changed);
    }
}
