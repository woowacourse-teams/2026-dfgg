package dfgg.application.match;

import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.ParticipantItemEvent;
import dfgg.domain.match.NormalizedMatchParticipantItemEvent;

/** 학습 후보를 만들기 전, 정규화된 구매와 구매 직전 상태를 연결한 결과다. */
public record ParticipantItemPurchaseSnapshot(
        NormalizedMatchParticipantItemEvent purchase,
        ParticipantItemEvent sourceEvent,
        ItemInventory inventoryBefore,
        Status status,
        Reason reason
) {

    public enum Status {
        READY,
        EXCLUDED,
        UNRESOLVED
    }

    public enum Reason {
        OUT_OF_RECOMMENDATION_SCOPE,
        PURCHASE_UNDONE,
        CANCELLATION_UNRESOLVED,
        SOURCE_EVENT_MISSING,
        SOURCE_EVENT_AMBIGUOUS,
        SOURCE_EVENT_MISMATCH,
        INVENTORY_UNRESOLVED,
        GOLD_RANGE_MISSING,
        GOLD_RANGE_INVALID
    }
}
