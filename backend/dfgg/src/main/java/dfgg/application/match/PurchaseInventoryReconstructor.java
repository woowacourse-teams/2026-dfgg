package dfgg.application.match;

import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.ParticipantItemEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 정규화된 전체 아이템 행동을 재생해 각 구매 거래 직전의 보유 수량을 복원한다. */
@Component
public class PurchaseInventoryReconstructor {

    private final PurchaseMaterialLinker materialLinker;

    public PurchaseInventoryReconstructor(PurchaseMaterialLinker materialLinker) {
        this.materialLinker = materialLinker;
    }

    /** 구매마다 복원 결과·실패 이유·구매 취소 여부를 반환한다. */
    public Map<ParticipantItemEvent, PurchaseState> reconstruct(List<ParticipantItemEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("전체 아이템 이벤트가 필요합니다.");
        }
        Map<Integer, List<ParticipantItemEvent>> byParticipant = groupByParticipant(events);
        Map<ParticipantItemEvent, PurchaseState> states = new HashMap<>();
        for (List<ParticipantItemEvent> participantEvents : byParticipant.values()) {
            replay(participantEvents, states);
        }
        return Map.copyOf(states);
    }

    /** 기존 호출부를 위해 복원 가능한 구매의 Inventory만 반환한다. */
    public Map<ParticipantItemEvent, ItemInventory> beforePurchases(List<ParticipantItemEvent> events) {
        Map<ParticipantItemEvent, ItemInventory> snapshots = new HashMap<>();
        reconstruct(events).forEach((event, state) -> {
            if (state.inventoryBefore() != null) {
                snapshots.put(event, state.inventoryBefore());
            }
        });
        return Map.copyOf(snapshots);
    }

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

    private void replay(List<ParticipantItemEvent> events, Map<ParticipantItemEvent, PurchaseState> states) {
        ItemInventory inventory = ItemInventory.empty();
        ArrayDeque<Transaction> transactions = new ArrayDeque<>();
        boolean reliable = true;
        InventoryFailure firstFailure = null;
        int previousOrder = 0;
        String matchId = events.getFirst().matchId();

        for (ParticipantItemEvent event : events) {
            validateOrder(event, matchId, previousOrder);
            previousOrder = event.eventOrder();

            if (!reliable) {
                recordUnresolvedPurchase(event, states, firstFailure);
                continue;
            }

            if ("ITEM_PURCHASED".equals(event.eventType())) {
                Optional<ItemInventory> before = beforePurchase(event, events, inventory);
                if (before.isEmpty()) {
                    firstFailure = InventoryFailure.COMBINATION_MATERIAL_UNRESOLVED;
                    states.put(event, PurchaseState.unresolved(firstFailure));
                    reliable = false;
                    continue;
                }
                Optional<ItemInventory> after = add(event.itemId(), inventory);
                if (after.isEmpty()) {
                    firstFailure = InventoryFailure.INVALID_ITEM_EVENT;
                    states.put(event, PurchaseState.unresolved(firstFailure));
                    reliable = false;
                    continue;
                }
                ItemInventory nextInventory = after.orElseThrow();
                states.put(event, PurchaseState.restored(before.orElseThrow()));
                transactions.addLast(new Transaction(TransactionType.PURCHASE, event.itemId(),
                        before.orElseThrow(), nextInventory, event));
                inventory = nextInventory;
                continue;
            }

            if ("ITEM_SOLD".equals(event.eventType())) {
                Optional<ItemInventory> after = remove(event.itemId(), inventory);
                if (after.isEmpty()) {
                    firstFailure = InventoryFailure.ITEM_NOT_IN_INVENTORY;
                    reliable = false;
                    continue;
                }
                ItemInventory nextInventory = after.orElseThrow();
                transactions.addLast(new Transaction(TransactionType.SALE, event.itemId(),
                        inventory, nextInventory, null));
                inventory = nextInventory;
                continue;
            }

            if ("ITEM_DESTROYED".equals(event.eventType())) {
                Optional<ItemInventory> after = remove(event.itemId(), inventory);
                if (after.isEmpty()) {
                    firstFailure = InventoryFailure.ITEM_NOT_IN_INVENTORY;
                    reliable = false;
                    continue;
                }
                inventory = after.orElseThrow();
                continue;
            }

            if ("ITEM_UNDO".equals(event.eventType())) {
                Optional<UndoResult> undone = undo(event, inventory, transactions);
                if (undone.isEmpty()) {
                    markUnresolvedUndo(event, transactions, states);
                    firstFailure = InventoryFailure.UNDO_TRANSACTION_UNRESOLVED;
                    reliable = false;
                    continue;
                }
                UndoResult result = undone.orElseThrow();
                inventory = result.inventory();
                transactions.removeLast();
                if (result.undonePurchase() != null) {
                    states.computeIfPresent(result.undonePurchase(),
                            (ignored, state) -> state.purchaseWasUndone());
                }
                continue;
            }

            firstFailure = InventoryFailure.UNSUPPORTED_ITEM_EVENT;
            reliable = false;
        }
    }

    private void recordUnresolvedPurchase(ParticipantItemEvent event,
                                          Map<ParticipantItemEvent, PurchaseState> states,
                                          InventoryFailure failure) {
        if ("ITEM_PURCHASED".equals(event.eventType())) {
            states.put(event, PurchaseState.unresolved(failure));
        }
    }

    private Optional<UndoResult> undo(ParticipantItemEvent event, ItemInventory inventory,
                                      ArrayDeque<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return Optional.empty();
        }
        Transaction transaction = transactions.peekLast();
        if (!transaction.after().equals(inventory) || !transaction.matchesUndo(event)) {
            return Optional.empty();
        }
        ParticipantItemEvent undonePurchase = transaction.type() == TransactionType.PURCHASE
                ? transaction.sourcePurchase() : null;
        return Optional.of(new UndoResult(transaction.before(), undonePurchase));
    }

    private void markUnresolvedUndo(ParticipantItemEvent undo,
                                    ArrayDeque<Transaction> transactions,
                                    Map<ParticipantItemEvent, PurchaseState> states) {
        Integer possiblePurchaseItemId = undo.beforeItemId() != null && undo.beforeItemId() > 0
                && (undo.afterItemId() == null || undo.afterItemId() == 0) ? undo.beforeItemId() : null;
        for (Transaction transaction : transactions) {
            if (transaction.type() != TransactionType.PURCHASE || transaction.sourcePurchase() == null) {
                continue;
            }
            if (possiblePurchaseItemId == null || transaction.itemId() == possiblePurchaseItemId) {
                states.computeIfPresent(transaction.sourcePurchase(),
                        (ignored, state) -> state.cancellationIsUnresolved());
            }
        }
    }

    private void validateOrder(ParticipantItemEvent event, String matchId, int previousOrder) {
        if (!matchId.equals(event.matchId()) || event.eventOrder() != previousOrder + 1) {
            throw new IllegalArgumentException("한 경기의 참가자별 전체 아이템 이벤트를 eventOrder 순으로 전달해야 합니다.");
        }
    }

    private Optional<ItemInventory> beforePurchase(ParticipantItemEvent purchase,
                                                   List<ParticipantItemEvent> events, ItemInventory inventory) {
        Optional<List<Integer>> materials = materialLinker.link(purchase, events);
        if (materials.isEmpty()) {
            return hasUnlinkedDestroy(purchase, events) ? Optional.empty() : Optional.of(inventory);
        }
        return Optional.of(inventory.restore(materials.orElseThrow()));
    }

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

    public record PurchaseState(ItemInventory inventoryBefore, InventoryFailure inventoryFailure,
                                boolean purchaseUndone, boolean cancellationUnresolved) {

        private static PurchaseState restored(ItemInventory inventory) {
            return new PurchaseState(inventory, null, false, false);
        }

        private static PurchaseState unresolved(InventoryFailure failure) {
            return new PurchaseState(null, failure, false, false);
        }

        private PurchaseState purchaseWasUndone() {
            return new PurchaseState(inventoryBefore, inventoryFailure, true, cancellationUnresolved);
        }

        private PurchaseState cancellationIsUnresolved() {
            return new PurchaseState(inventoryBefore, inventoryFailure, purchaseUndone, true);
        }
    }

    public enum InventoryFailure {
        COMBINATION_MATERIAL_UNRESOLVED,
        ITEM_NOT_IN_INVENTORY,
        INVALID_ITEM_EVENT,
        UNDO_TRANSACTION_UNRESOLVED,
        UNSUPPORTED_ITEM_EVENT
    }

    private enum TransactionType {
        PURCHASE,
        SALE
    }

    private record Transaction(TransactionType type, int itemId, ItemInventory before,
                               ItemInventory after, ParticipantItemEvent sourcePurchase) {

        private boolean matchesUndo(ParticipantItemEvent event) {
            if (type == TransactionType.PURCHASE) {
                return Objects.equals(event.beforeItemId(), itemId)
                        && Objects.equals(event.afterItemId(), 0);
            }
            return Objects.equals(event.beforeItemId(), 0)
                    && Objects.equals(event.afterItemId(), itemId);
        }
    }

    private record UndoResult(ItemInventory inventory, ParticipantItemEvent undonePurchase) {
    }
}
