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
import java.util.Set;
import org.springframework.stereotype.Component;

/** 정규화된 전체 아이템 행동을 재생해 각 구매 거래 직전의 보유 수량을 복원한다. */
@Component
public class PurchaseInventoryReconstructor {

    private static final Set<Integer> INSTANT_ELIXIRS = Set.of(2138, 2139, 2140);
    private static final Map<Integer, Integer> TRANSFORMS = Map.of(
            3004, 3042, 3003, 3040, 3865, 3866, 3866, 3867, 2420, 2421);
    private static final Map<Integer, Integer> MID_BOOTS = Map.of(
            3009, 3170, 3158, 3171, 3006, 3172, 3111, 3173, 3047, 3174, 3020, 3175, 3008, 3168);

    private Optional<ItemInventory> afterDestroy(ParticipantItemEvent event,
            List<ParticipantItemEvent> events, ItemInventory inventory, boolean enabled, boolean midQuestCompleted) {
        Integer itemId = event.itemId();
        if (itemId == null || itemId <= 0) {
            return Optional.empty();
        }
        if (enabled && ItemInventory.isNonInventoryMarker(itemId)) {
            return Optional.of(inventory);
        }
        // 제거 이벤트 자체가 직전 보유의 근거다. 이전 구매 시점으로 소급하지 않는다.
        ItemInventory before = inventory.quantityOf(itemId) == 0 ? inventory.add(itemId) : inventory;
        if (enabled && itemId == 2422 && atMoment(event, events, "ITEM_DESTROYED", 1202)) {
            return Optional.of(before); // Bot 퀘스트 슬롯 이동은 보유 자체의 소멸이 아니다.
        }
        if (enabled && (INSTANT_ELIXIRS.contains(itemId) || midQuestCompleted && MID_BOOTS.containsKey(itemId))
                && atMoment(event, events, "ITEM_PURCHASED", itemId)) {
            return Optional.of(inventory);
        }
        ItemInventory after = before.remove(itemId).orElseThrow();
        if (enabled && MID_BOOTS.containsKey(itemId) && atMoment(event, events, "ITEM_DESTROYED", 1201)) {
            return Optional.of(after.add(MID_BOOTS.get(itemId)));
        }
        if (enabled && TRANSFORMS.containsKey(itemId) && !hasPurchaseAtMoment(event, events)) {
            return Optional.of(after.add(TRANSFORMS.get(itemId)));
        }
        return Optional.of(after);
    }

    private ItemInventory afterPurchase(ParticipantItemEvent purchase, List<ParticipantItemEvent> events,
            ItemInventory inventory, boolean enabled, boolean midQuestCompleted) {
        int itemId = purchase.itemId();
        if (enabled && atMoment(purchase, events, "ITEM_DESTROYED", itemId)) {
            if (INSTANT_ELIXIRS.contains(itemId)) {
                return inventory;
            }
            if (midQuestCompleted && MID_BOOTS.containsKey(itemId)) {
                return inventory.add(MID_BOOTS.get(itemId));
            }
        }
        return inventory.add(itemId);
    }

    private boolean isMaterial(ParticipantItemEvent purchase, Integer itemId,
            boolean enabled, boolean midQuestCompleted) {
        return !enabled || !ItemInventory.isNonInventoryMarker(itemId)
                && !(itemId.equals(purchase.itemId()) && (INSTANT_ELIXIRS.contains(itemId)
                || midQuestCompleted && MID_BOOTS.containsKey(itemId)));
    }

    private boolean hasPurchaseAtMoment(ParticipantItemEvent event, List<ParticipantItemEvent> events) {
        return events.stream().anyMatch(other -> sameMoment(event, other)
                && "ITEM_PURCHASED".equals(other.eventType()));
    }

    private boolean atMoment(ParticipantItemEvent event, List<ParticipantItemEvent> events, String type, int itemId) {
        return events.stream().anyMatch(other -> sameMoment(event, other)
                && type.equals(other.eventType()) && Objects.equals(other.itemId(), itemId));
    }

    private boolean sameMoment(ParticipantItemEvent first, ParticipantItemEvent second) {
        return first.gameTimeMs() != null && first.sourceFrameIndex() == second.sourceFrameIndex()
                && first.gameTimeMs().equals(second.gameTimeMs());
    }

    private final PurchaseMaterialLinker materialLinker;

    public PurchaseInventoryReconstructor(PurchaseMaterialLinker materialLinker) {
        this.materialLinker = materialLinker;
    }

    /** 구매마다 복원 결과·실패 이유·구매 취소 여부를 반환한다. */
    public Map<ParticipantItemEvent, PurchaseState> reconstruct(List<ParticipantItemEvent> events) {
        return reconstruct(events, null);
    }

    public Map<ParticipantItemEvent, PurchaseState> reconstruct(List<ParticipantItemEvent> events, String patch) {
        return reconstruct(events, patch, Set.of());
    }

    public Map<ParticipantItemEvent, PurchaseState> reconstruct(List<ParticipantItemEvent> events, String patch,
                                                               Set<Integer> supportParticipants) {
        if (events == null) {
            throw new IllegalArgumentException("전체 아이템 이벤트가 필요합니다.");
        }
        Map<Integer, List<ParticipantItemEvent>> byParticipant = groupByParticipant(events);
        Map<ParticipantItemEvent, PurchaseState> states = new HashMap<>();
        for (List<ParticipantItemEvent> participantEvents : byParticipant.values()) {
            replay(participantEvents, states, "16.18".equals(patch),
                    supportParticipants.contains(participantEvents.getFirst().participantId()));
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

    private void replay(List<ParticipantItemEvent> events, Map<ParticipantItemEvent, PurchaseState> states,
                        boolean patchRulesEnabled, boolean supportParticipant) {
        boolean midQuestCompleted = false;
        ItemInventory inventory = patchRulesEnabled && supportParticipant
                ? ItemInventory.empty().moveControlWardsToQuestSlot() : ItemInventory.empty();
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
                Optional<ItemInventory> before = beforePurchase(event, events, inventory, patchRulesEnabled, midQuestCompleted);
                if (before.isEmpty()) {
                    firstFailure = InventoryFailure.COMBINATION_MATERIAL_UNRESOLVED;
                    states.put(event, PurchaseState.unresolved(firstFailure));
                    reliable = false;
                    continue;
                }
                Optional<ItemInventory> after = event.itemId() == null || event.itemId() <= 0
                        ? Optional.empty() : Optional.of(afterPurchase(event, events, inventory, patchRulesEnabled, midQuestCompleted));
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
                if (patchRulesEnabled && Objects.equals(event.itemId(), 1202)) {
                    inventory = inventory.moveBootsToQuestSlot();
                }
                if (patchRulesEnabled && Objects.equals(event.itemId(), 1201)) {
                    midQuestCompleted = true;
                }
                if (patchRulesEnabled && Objects.equals(event.itemId(), 3867)
                        && !hasPurchaseAtMoment(event, events)) {
                    firstFailure = InventoryFailure.TRANSFORM_OUTPUT_UNOBSERVED;
                    reliable = false;
                    continue;
                }
                Optional<ItemInventory> after = afterDestroy(event, events, inventory,
                        patchRulesEnabled, midQuestCompleted);
                if (after.isEmpty()) {
                    firstFailure = InventoryFailure.INVALID_ITEM_EVENT;
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
                                                   List<ParticipantItemEvent> events, ItemInventory inventory,
                                                   boolean patchRulesEnabled, boolean midQuestCompleted) {
        Optional<List<Integer>> materials = materialLinker.link(purchase, events);
        if (materials.isEmpty()) {
            return hasUnlinkedDestroy(purchase, events) ? Optional.empty() : Optional.of(inventory);
        }
        return Optional.of(inventory.restore(materials.orElseThrow().stream()
                .filter(itemId -> isMaterial(purchase, itemId, patchRulesEnabled, midQuestCompleted)).toList()));
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
        UNSUPPORTED_ITEM_EVENT,
        TRANSFORM_OUTPUT_UNOBSERVED
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
