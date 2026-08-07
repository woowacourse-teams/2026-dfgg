package dfgg.domain.match;

import java.util.List;
import java.util.Objects;

public record NormalizedParticipant(
        String puuid,
        Integer participantId,
        Integer championId,
        Integer teamId,
        String position,
        Boolean win,
        List<Integer> finalCoreItemIds,
        List<Integer> coreItemPurchaseOrder,
        boolean coreItemPurchaseOrderComplete
) {

    public NormalizedParticipant {
        if (puuid == null || puuid.isBlank()) {
            throw new IllegalArgumentException("puuid must not be blank");
        }
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(championId, "championId must not be null");
        Objects.requireNonNull(teamId, "teamId must not be null");
        Objects.requireNonNull(win, "win must not be null");
        finalCoreItemIds = List.copyOf(Objects.requireNonNull(finalCoreItemIds, "finalCoreItemIds must not be null"));
        coreItemPurchaseOrder = List.copyOf(
                Objects.requireNonNull(coreItemPurchaseOrder, "coreItemPurchaseOrder must not be null")
        );
    }
}
