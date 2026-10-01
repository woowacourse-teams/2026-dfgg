package dfgg.application.match;

import dfgg.application.match.ParticipantItemPurchaseSnapshot.Reason;
import dfgg.application.match.ParticipantItemPurchaseSnapshot.Status;
import dfgg.domain.match.ParticipantItemEvent;
import dfgg.domain.match.NormalizedMatchParticipantItemEvent;
import dfgg.domain.match.NormalizedMatchParticipantItemEventRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정규화된 구매 행, 아이템 Fact, 구매 직전 Inventory와 Gold Range를 연결한다. */
@Service
public class ParticipantItemPurchaseSnapshotService {

    private final NormalizedMatchParticipantItemEventRepository eventRepository;
    private final PurchaseInventoryReconstructor inventoryReconstructor;
    private final PurchaseMaterialLinker materialLinker;

    public ParticipantItemPurchaseSnapshotService(
            NormalizedMatchParticipantItemEventRepository eventRepository,
            PurchaseInventoryReconstructor inventoryReconstructor,
            PurchaseMaterialLinker materialLinker
    ) {
        this.eventRepository = eventRepository;
        this.inventoryReconstructor = inventoryReconstructor;
        this.materialLinker = materialLinker;
    }

    /** 저장된 한 경기의 구매와 아이템 Fact를 연결해 원천 순번대로 반환한다. Raw JSON을 다시 읽지 않는다. */
    @Transactional(readOnly = true)
    public List<ParticipantItemPurchaseSnapshot> snapshotsForMatch(String matchId) {
        return snapshots(loadEvents(matchId));
    }

    /** 초기 정책: 아이템 이벤트 간 10초 공백으로 묶고 30초 초과 묶음은 사용하지 않는다. */
    @Transactional(readOnly = true)
    public List<ParticipantItemPurchaseBundle> bundlesForMatch(String matchId) {
        var normalizedEvents = loadEvents(matchId);
        if (normalizedEvents.stream().anyMatch(event -> !event.hasReplaySource())) {
            throw new IllegalStateException("구매 묶음 추출 전에 전체 아이템 이벤트 재정규화가 필요합니다.");
        }
        Map<ParticipantItemEvent, ParticipantItemPurchaseSnapshot> snapshotsBySource = new HashMap<>();
        for (var snapshot : snapshots(normalizedEvents)) {
            snapshotsBySource.put(snapshot.sourceEvent(), snapshot);
        }
        Map<Integer, List<ParticipantItemEvent>> byParticipant = new LinkedHashMap<>();
        for (var event : normalizedEvents) {
            byParticipant.computeIfAbsent(event.getParticipantId(), ignored -> new ArrayList<>())
                    .add(event.toParticipantItemEvent());
        }
        List<ParticipantItemPurchaseBundle> bundles = new ArrayList<>();
        for (var events : byParticipant.values()) {
            List<ParticipantItemEvent> segment = new ArrayList<>();
            for (var event : events) {
                if (!segment.isEmpty() && separatedByGap(segment.getLast(), event)) {
                    appendBundle(segment, events, snapshotsBySource, bundles);
                    segment.clear();
                }
                segment.add(event);
            }
            appendBundle(segment, events, snapshotsBySource, bundles);
        }
        return List.copyOf(bundles);
    }

    private boolean separatedByGap(ParticipantItemEvent previous, ParticipantItemEvent current) {
        return previous.gameTimeMs() != null && current.gameTimeMs() != null
                && current.gameTimeMs() - previous.gameTimeMs() >= ParticipantItemPurchaseBundle.EVENT_GAP_MS;
    }

    private void appendBundle(List<ParticipantItemEvent> segment, List<ParticipantItemEvent> participantEvents,
            Map<ParticipantItemEvent, ParticipantItemPurchaseSnapshot> snapshotsBySource,
            List<ParticipantItemPurchaseBundle> bundles) {
        var purchases = segment.stream().filter(event -> "ITEM_PURCHASED".equals(event.eventType()))
                .map(snapshotsBySource::get).toList();
        if (!purchases.isEmpty()) {
            bundles.add(ParticipantItemPurchaseBundle.from(segment, purchases, participantEvents, materialLinker));
        }
    }

    private List<NormalizedMatchParticipantItemEvent> loadEvents(String matchId) {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("경기 ID(matchId)가 필요합니다.");
        }
        return eventRepository.findByMatchIdOrderByParticipantIdAscEventOrderAsc(matchId);
    }

    private List<ParticipantItemPurchaseSnapshot> snapshots(List<NormalizedMatchParticipantItemEvent> normalizedEvents) {
        boolean replaySourceMissing = normalizedEvents.stream().anyMatch(event -> !event.hasReplaySource());
        List<ParticipantItemEvent> events = replaySourceMissing ? List.of() : normalizedEvents.stream()
                .map(NormalizedMatchParticipantItemEvent::toParticipantItemEvent).toList();
        Map<ParticipantItemEvent, PurchaseInventoryReconstructor.PurchaseState> inventoryStates =
                inventoryReconstructor.reconstruct(events,
                        normalizedEvents.isEmpty() ? null : normalizedEvents.getFirst().getPatch());
        List<ParticipantItemPurchaseSnapshot> snapshots = new ArrayList<>();
        for (NormalizedMatchParticipantItemEvent event : normalizedEvents) {
            if (event.isPurchase()) {
                snapshots.add(snapshot(event, replaySourceMissing, inventoryStates));
            }
        }
        return List.copyOf(snapshots);
    }

    private ParticipantItemPurchaseSnapshot snapshot(
            NormalizedMatchParticipantItemEvent purchase, boolean replaySourceMissing,
            Map<ParticipantItemEvent, PurchaseInventoryReconstructor.PurchaseState> inventoryStates) {
        if (replaySourceMissing) {
            return result(purchase, null, null, Status.UNRESOLVED, Reason.SOURCE_EVENT_MISSING);
        }
        ParticipantItemEvent sourceEvent = purchase.toParticipantItemEvent();
        if (!purchase.getPurchaseType().isComponentRecommendationTarget()) {
            return result(purchase, sourceEvent, null, Status.EXCLUDED, Reason.OUT_OF_RECOMMENDATION_SCOPE);
        }

        PurchaseInventoryReconstructor.PurchaseState state = inventoryStates.get(sourceEvent);
        if (state == null) {
            return result(purchase, sourceEvent, null, Status.UNRESOLVED, Reason.INVENTORY_UNRESOLVED);
        }
        if (state.cancellationUnresolved()) {
            return result(purchase, sourceEvent, state.inventoryBefore(), Status.UNRESOLVED,
                    Reason.CANCELLATION_UNRESOLVED);
        }
        if (state.purchaseUndone()) {
            return result(purchase, sourceEvent, state.inventoryBefore(), Status.EXCLUDED, Reason.PURCHASE_UNDONE);
        }
        if (state.inventoryBefore() == null || state.inventoryFailure() != null) {
            Reason reason = state.inventoryFailure() == null ? Reason.INVENTORY_UNRESOLVED
                    : switch (state.inventoryFailure()) {
                        case TRANSFORM_OUTPUT_UNOBSERVED -> Reason.TRANSFORM_OUTPUT_UNOBSERVED;
                        default -> Reason.INVENTORY_UNRESOLVED;
                    };
            return result(purchase, sourceEvent, null, Status.UNRESOLVED, reason);
        }
        if (purchase.getGoldLower() == null && purchase.getGoldUpper() == null) {
            return result(purchase, sourceEvent, state.inventoryBefore(), Status.EXCLUDED, Reason.GOLD_RANGE_MISSING);
        }
        if (!validGoldRange(purchase)) {
            return result(purchase, sourceEvent, state.inventoryBefore(), Status.UNRESOLVED,
                    Reason.GOLD_RANGE_INVALID);
        }
        return result(purchase, sourceEvent, state.inventoryBefore(), Status.READY, null);
    }

    private boolean validGoldRange(NormalizedMatchParticipantItemEvent purchase) {
        Integer lower = purchase.getGoldLower();
        Integer upper = purchase.getGoldUpper();
        return lower != null && upper != null && lower >= 0 && upper >= lower;
    }

    private ParticipantItemPurchaseSnapshot result(NormalizedMatchParticipantItemEvent purchase,
                                                   ParticipantItemEvent sourceEvent,
                                                   dfgg.domain.match.ItemInventory inventoryBefore,
                                                   Status status, Reason reason) {
        return new ParticipantItemPurchaseSnapshot(purchase, sourceEvent, inventoryBefore, status, reason);
    }
}
