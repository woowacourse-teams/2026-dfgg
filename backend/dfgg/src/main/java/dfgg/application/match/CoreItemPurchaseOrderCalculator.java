package dfgg.application.match;

import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CoreItemPurchaseOrderCalculator {

    private static final String ITEM_PURCHASED = "ITEM_PURCHASED";
    private static final String ITEM_SOLD = "ITEM_SOLD";
    private static final String ITEM_UNDO = "ITEM_UNDO";
    private static final String ITEM_DESTROYED = "ITEM_DESTROYED";

    /**
     * 서포터 퀘스트 3티어(세계의 결실). 이게 파괴되는 시점이 최종형을 획득한 시점이다.
     */
    private static final int SUPPORT_QUEST_TIER3_ITEM = 3867;

    /**
     * 서포터 퀘스트로 자동 승급되는 최종형들. 이 아이템들은 획득 이벤트가 없을 수 있다 —
     * 계열 전체(3865 → 3866 → 3867 → 최종형)가 지급·자동 승급이라 파괴 이벤트로만 나타난다.
     * <p>
     * 구매 이벤트가 있으면 그것을 그대로 쓰고, 없을 때만 앵커로 보정한다.
     * 실제로 구매 이벤트가 있는 경우도 존재하므로 구매도 사용한다. (판매 후, 재구매 가능)
     */
    private static final Set<Integer> SUPPORT_QUEST_FINAL_ITEMS =
            Set.of(3869, 3870, 3871, 3876, 3877, 6621);

    public Optional<List<Integer>> calculate(
            MatchTimelineResponse timeline,
            int participantId,
            Collection<Integer> finalCoreItemIds,
            Collection<Integer> coreItemIds
    ) {
        Objects.requireNonNull(timeline, "timeline must not be null");
        Objects.requireNonNull(finalCoreItemIds, "finalCoreItemIds must not be null");
        Objects.requireNonNull(coreItemIds, "coreItemIds must not be null");

        Set<Integer> coreItems = new HashSet<>(coreItemIds);
        Set<Integer> finalItems = new HashSet<>(finalCoreItemIds);
        finalItems.retainAll(coreItems);
        if (finalItems.isEmpty()) {
            return Optional.of(List.of());
        }
        if (timeline.info() == null || timeline.info().frames() == null) {
            return Optional.empty();
        }

        List<IndexedEvent> events = flattenEvents(timeline);
        LinkedHashSet<Integer> ownedCoreItems = new LinkedHashSet<>();
        List<Integer> purchaseOrder = new ArrayList<>();
        Map<Integer, Long> acquiredAt = new HashMap<>();
        Long supportQuestCompletedAt = null;

        for (IndexedEvent indexedEvent : events) {
            MatchTimelineResponse.Event event = indexedEvent.event();
            if (!Objects.equals(event.participantId(), participantId)) {
                continue;
            }
            if (event.type() == null) {
                continue;
            }
            switch (event.type()) {
                case ITEM_PURCHASED -> addItem(
                        event.itemId(), coreItems, ownedCoreItems, purchaseOrder, acquiredAt, event.timestamp());
                case ITEM_SOLD -> removeItem(event.itemId(), coreItems, ownedCoreItems, purchaseOrder);
                case ITEM_UNDO -> {
                    removeItem(event.beforeId(), coreItems, ownedCoreItems, purchaseOrder);
                    addItem(event.afterId(), coreItems, ownedCoreItems, purchaseOrder,
                            acquiredAt, event.timestamp());
                }
                case ITEM_DESTROYED -> {
                    if (Objects.equals(event.itemId(), SUPPORT_QUEST_TIER3_ITEM)) {
                        supportQuestCompletedAt = event.timestamp();
                    }
                }
                default -> {
                    // Item order is determined only by item inventory events.
                }
            }
        }

        insertSupportQuestItems(
                finalItems, ownedCoreItems, purchaseOrder, acquiredAt, supportQuestCompletedAt);

        if (!ownedCoreItems.containsAll(finalItems)) {
            return Optional.empty();
        }

        List<Integer> finalPurchaseOrder = purchaseOrder.stream()
                .filter(finalItems::contains)
                .toList();
        if (finalPurchaseOrder.size() != finalItems.size()) {
            return Optional.empty();
        }
        return Optional.of(finalPurchaseOrder);
    }

    private List<IndexedEvent> flattenEvents(MatchTimelineResponse timeline) {
        List<IndexedEvent> events = new ArrayList<>();
        int sequence = 0;
        for (MatchTimelineResponse.Frame frame : timeline.info().frames()) {
            if (frame == null || frame.events() == null) {
                continue;
            }
            for (MatchTimelineResponse.Event event : frame.events()) {
                if (event != null) {
                    events.add(new IndexedEvent(event, sequence++));
                }
            }
        }
        return events.stream()
                .sorted(Comparator
                        .comparing(IndexedEvent::timestamp, Comparator.nullsLast(Long::compareTo))
                        .thenComparingInt(IndexedEvent::sequence))
                .toList();
    }

    /**
     * 구매 기록이 없는 서포터 퀘스트 최종형을 퀘스트 완료 시점에 끼워 넣는다.
     *
     * <p>이 보정이 없으면 추적 불가능한 아이템 하나 때문에 <b>정상적으로 기록된 나머지 아이템의
     * 순서까지 통째로 버려진다</b>. 실측상 서포터의 90%가 이 이유로 버려지고 있었다
     * (사용 가능률 9.7% vs 다른 포지션 96%+).
     *
     * <p>타임라인에 가짜 이벤트를 만들지 않는다 — 어떤 아이템인지는 매치 데이터가 이미 알려주고,
     * 언제인지만 3티어 파괴 시점에서 가져온다. 그 근거가 없으면 지어내지 않고 그대로 실패시킨다.
     */
    private void insertSupportQuestItems(
            Set<Integer> finalItems,
            Set<Integer> ownedCoreItems,
            List<Integer> purchaseOrder,
            Map<Integer, Long> acquiredAt,
            Long supportQuestCompletedAt
    ) {
        if (supportQuestCompletedAt == null) {
            return;
        }
        List<Integer> missingQuestItems = finalItems.stream()
                .filter(itemId -> !ownedCoreItems.contains(itemId))
                .filter(SUPPORT_QUEST_FINAL_ITEMS::contains)
                .sorted()
                .toList();

        for (Integer questItem : missingQuestItems) {
            purchaseOrder.add(insertIndexFor(purchaseOrder, acquiredAt, supportQuestCompletedAt), questItem);
            acquiredAt.put(questItem, supportQuestCompletedAt);
            ownedCoreItems.add(questItem);
        }
    }

    private int insertIndexFor(List<Integer> purchaseOrder, Map<Integer, Long> acquiredAt, long timestamp) {
        for (int index = 0; index < purchaseOrder.size(); index++) {
            Long itemAcquiredAt = acquiredAt.get(purchaseOrder.get(index));
            if (itemAcquiredAt != null && itemAcquiredAt > timestamp) {
                return index;
            }
        }
        return purchaseOrder.size();
    }

    private void addItem(
            Integer itemId,
            Set<Integer> coreItems,
            Set<Integer> ownedCoreItems,
            List<Integer> purchaseOrder,
            Map<Integer, Long> acquiredAt,
            Long timestamp
    ) {
        if (itemId == null || !coreItems.contains(itemId) || !ownedCoreItems.add(itemId)) {
            return;
        }
        purchaseOrder.add(itemId);
        if (timestamp != null) {
            acquiredAt.put(itemId, timestamp);
        }
    }

    private void removeItem(
            Integer itemId,
            Set<Integer> coreItems,
            Set<Integer> ownedCoreItems,
            List<Integer> purchaseOrder
    ) {
        if (itemId == null || !coreItems.contains(itemId) || !ownedCoreItems.remove(itemId)) {
            return;
        }
        purchaseOrder.remove(itemId);
    }

    private record IndexedEvent(MatchTimelineResponse.Event event, int sequence) {

        private Long timestamp() {
            return event.timestamp();
        }
    }
}
