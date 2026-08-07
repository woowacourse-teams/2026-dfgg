package dfgg.application;

import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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

        for (IndexedEvent indexedEvent : events) {
            MatchTimelineResponse.Event event = indexedEvent.event();
            if (!Objects.equals(event.participantId(), participantId)) {
                continue;
            }
            if (event.type() == null) {
                continue;
            }
            switch (event.type()) {
                case ITEM_PURCHASED -> addItem(event.itemId(), coreItems, ownedCoreItems, purchaseOrder);
                case ITEM_SOLD -> removeItem(event.itemId(), coreItems, ownedCoreItems, purchaseOrder);
                case ITEM_UNDO -> {
                    removeItem(event.beforeId(), coreItems, ownedCoreItems, purchaseOrder);
                    addItem(event.afterId(), coreItems, ownedCoreItems, purchaseOrder);
                }
                default -> {
                    // Item order is determined only by item inventory events.
                }
            }
        }

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

    private void addItem(
            Integer itemId,
            Set<Integer> coreItems,
            Set<Integer> ownedCoreItems,
            List<Integer> purchaseOrder
    ) {
        if (itemId == null || !coreItems.contains(itemId) || !ownedCoreItems.add(itemId)) {
            return;
        }
        purchaseOrder.add(itemId);
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
