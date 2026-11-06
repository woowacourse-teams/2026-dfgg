package dfgg.application.match;

import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.ParticipantItemEvent;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 한 번의 관측 구매 묶음. READY는 후보 생성 전의 사용 가능 여부이며 최적 구매를 뜻하지 않는다. */
public record ParticipantItemPurchaseBundle(
        List<ParticipantItemPurchaseSnapshot> purchases,
        Set<Reason> exclusionReasons
) {
    public static final long EVENT_GAP_MS = 10_000;
    public static final long MAX_DURATION_MS = 30_000;

    public ParticipantItemPurchaseBundle {
        purchases = List.copyOf(purchases);
        exclusionReasons = Set.copyOf(exclusionReasons);
        if (purchases.isEmpty()) {
            throw new IllegalArgumentException("구매 묶음에는 구매가 한 건 이상 필요합니다.");
        }
    }

    static ParticipantItemPurchaseBundle from(List<ParticipantItemEvent> segment,
            List<ParticipantItemPurchaseSnapshot> purchases, List<ParticipantItemEvent> participantEvents,
            PurchaseMaterialLinker materialLinker) {
        Set<Reason> reasons = EnumSet.noneOf(Reason.class);
        inspectTimes(segment, reasons);
        for (ParticipantItemEvent event : segment) {
            if ("ITEM_SOLD".equals(event.eventType())) {
                reasons.add(Reason.SALE_INCLUDED);
            }
            if ("ITEM_UNDO".equals(event.eventType())) {
                reasons.add(Reason.UNDO_INCLUDED);
            }
            if (!Set.of("ITEM_PURCHASED", "ITEM_SOLD", "ITEM_DESTROYED", "ITEM_UNDO")
                    .contains(event.eventType())) {
                reasons.add(Reason.INVENTORY_UNRESOLVED);
            }
        }
        inspectPurchases(purchases, reasons);
        inspectCombinations(purchases, participantEvents, materialLinker, reasons);
        return new ParticipantItemPurchaseBundle(purchases, reasons);
    }

    private static void inspectTimes(List<ParticipantItemEvent> events, Set<Reason> reasons) {
        Long previous = null;
        for (ParticipantItemEvent event : events) {
            Long time = event.gameTimeMs();
            if (time == null || time < 0 || previous != null && time < previous) {
                reasons.add(Reason.EVENT_TIME_INVALID);
            }
            previous = time;
        }
        Long start = events.getFirst().gameTimeMs();
        Long end = events.getLast().gameTimeMs();
        if (start != null && end != null && end - start > MAX_DURATION_MS) {
            reasons.add(Reason.DURATION_EXCEEDED);
        }
    }

    private static void inspectPurchases(List<ParticipantItemPurchaseSnapshot> purchases, Set<Reason> reasons) {
        for (ParticipantItemPurchaseSnapshot snapshot : purchases) {
            if (!snapshot.purchase().getPurchaseType().isComponentRecommendationTarget()) {
                reasons.add(Reason.MIXED_PURCHASE_TYPES);
            }
            if (snapshot.reason() == ParticipantItemPurchaseSnapshot.Reason.PURCHASE_UNDONE) {
                reasons.add(Reason.PURCHASE_UNDONE);
            }
            if (snapshot.reason() == ParticipantItemPurchaseSnapshot.Reason.CANCELLATION_UNRESOLVED) {
                reasons.add(Reason.CANCELLATION_UNRESOLVED);
            }
            if (snapshot.purchase().getPurchaseType().isComponentRecommendationTarget()
                    && snapshot.inventoryBefore() == null) {
                reasons.add(Reason.INVENTORY_UNRESOLVED);
            }
            if (snapshot.purchase().getItemCost() == null) {
                reasons.add(Reason.COST_UNKNOWN);
            }
        }
        var first = purchases.getFirst().purchase();
        if (first.getGoldLower() == null && first.getGoldUpper() == null) {
            reasons.add(Reason.GOLD_RANGE_MISSING);
            return;
        }
        if (first.getGoldLower() == null || first.getGoldUpper() == null
                || first.getGoldLower() < 0 || first.getGoldUpper() < first.getGoldLower()) {
            reasons.add(Reason.GOLD_RANGE_INVALID);
        }
    }

    private static void inspectCombinations(List<ParticipantItemPurchaseSnapshot> purchases,
            List<ParticipantItemEvent> participantEvents, PurchaseMaterialLinker materialLinker, Set<Reason> reasons) {
        Set<Integer> boughtInBundle = new java.util.HashSet<>();
        for (ParticipantItemPurchaseSnapshot snapshot : purchases) {
            var materials = materialLinker.link(snapshot.sourceEvent(), participantEvents);
            if (materials.isEmpty()) {
                reasons.add(Reason.COMBINATION_UNRESOLVED);
            } else if (materials.orElseThrow().stream().anyMatch(boughtInBundle::contains)) {
                // 같은 종류의 기존 재료도 있었다면 어느 것을 소비했는지 불명확하므로 첫 버전은 제외한다.
                reasons.add(Reason.RECOMBINATION_INCLUDED);
            }
            boughtInBundle.add(snapshot.purchase().getItemId());
        }
    }

    public boolean ready() {
        return exclusionReasons.isEmpty();
    }

    public String matchId() {
        return purchases.getFirst().purchase().getMatchId();
    }

    public int participantId() {
        return purchases.getFirst().purchase().getParticipantId();
    }

    public int startPurchaseOrder() {
        return purchases.getFirst().purchase().getPurchaseOrder();
    }

    public int endPurchaseOrder() {
        return purchases.getLast().purchase().getPurchaseOrder();
    }

    public ItemInventory inventoryBefore() {
        return purchases.getFirst().inventoryBefore();
    }

    public Integer goldLower() {
        return purchases.getFirst().purchase().getGoldLower();
    }

    public Integer goldUpper() {
        return purchases.getFirst().purchase().getGoldUpper();
    }

    public Map<Integer, Integer> itemQuantities() {
        Map<Integer, Integer> quantities = new HashMap<>();
        for (var purchase : purchases) {
            quantities.merge(purchase.purchase().getItemId(), 1, Integer::sum);
        }
        return Map.copyOf(quantities);
    }

    /** 실제 구매별 지출 비용 가설의 합계. 후보 묶음 비용 계산은 다음 작업이다. */
    public Long totalCost() {
        if (purchases.stream().anyMatch(snapshot -> snapshot.purchase().getItemCost() == null)) {
            return null;
        }
        return purchases.stream().mapToLong(snapshot -> snapshot.purchase().getItemCost()).sum();
    }

    public enum Reason {
        SALE_INCLUDED, UNDO_INCLUDED, PURCHASE_UNDONE, CANCELLATION_UNRESOLVED,
        MIXED_PURCHASE_TYPES, RECOMBINATION_INCLUDED, COMBINATION_UNRESOLVED,
        INVENTORY_UNRESOLVED, GOLD_RANGE_MISSING, GOLD_RANGE_INVALID, COST_UNKNOWN,
        DURATION_EXCEEDED, EVENT_TIME_INVALID
    }
}
