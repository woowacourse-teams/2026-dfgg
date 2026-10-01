package dfgg.application.match;

import dfgg.application.match.ParticipantItemPurchaseSnapshot.Reason;
import dfgg.application.match.ParticipantItemPurchaseSnapshot.Status;
import dfgg.domain.match.ParticipantItemEvent;
import dfgg.domain.match.NormalizedMatchParticipantItemEvent;
import dfgg.domain.match.NormalizedMatchParticipantItemEventRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정규화된 구매 행, 아이템 Fact, 구매 직전 Inventory와 Gold Range를 연결한다. */
@Service
public class ParticipantItemPurchaseSnapshotService {

    private final NormalizedMatchParticipantItemEventRepository eventRepository;
    private final PurchaseInventoryReconstructor inventoryReconstructor;

    public ParticipantItemPurchaseSnapshotService(
            NormalizedMatchParticipantItemEventRepository eventRepository,
            PurchaseInventoryReconstructor inventoryReconstructor
    ) {
        this.eventRepository = eventRepository;
        this.inventoryReconstructor = inventoryReconstructor;
    }

    /** 저장된 한 경기의 구매와 아이템 Fact를 연결해 원천 순번대로 반환한다. Raw JSON을 다시 읽지 않는다. */
    @Transactional(readOnly = true)
    public List<ParticipantItemPurchaseSnapshot> snapshotsForMatch(String matchId) {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("경기 ID(matchId)가 필요합니다.");
        }
        List<NormalizedMatchParticipantItemEvent> normalizedEvents = eventRepository
                .findByMatchIdOrderByParticipantIdAscEventOrderAsc(matchId);
        boolean replaySourceMissing = normalizedEvents.stream().anyMatch(event -> !event.hasReplaySource());
        List<ParticipantItemEvent> events = replaySourceMissing ? List.of() : normalizedEvents.stream()
                .map(NormalizedMatchParticipantItemEvent::toParticipantItemEvent).toList();
        Map<ParticipantItemEvent, PurchaseInventoryReconstructor.PurchaseState> inventoryStates =
                inventoryReconstructor.reconstruct(events);
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
            return result(purchase, sourceEvent, null, Status.UNRESOLVED, Reason.INVENTORY_UNRESOLVED);
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
