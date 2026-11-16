package dfgg.application.match;

import dfgg.domain.match.CoreItemChange;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 타임라인 이벤트 중 Core 보유를 바꾸는 것(구매·판매·되돌리기)을 {@link CoreItemChange}로 읽는다.
 * Core 구매 순서 계산과 경기 상태 복원이 함께 써서, 어떤 이벤트가 보유를 바꾸는지가 한 곳에만 있다.
 */
public final class CoreItemChangeReader {

    private static final String ITEM_PURCHASED = "ITEM_PURCHASED";
    private static final String ITEM_SOLD = "ITEM_SOLD";
    private static final String ITEM_UNDO = "ITEM_UNDO";

    private final Set<Integer> coreItemIds;

    public CoreItemChangeReader(Collection<Integer> coreItemIds) {
        this.coreItemIds = new HashSet<>(coreItemIds);
    }

    /** 보유를 바꾸지 않는 이벤트는 빈 결과다. Core가 아닌 아이템은 없음(null)으로 담는다. */
    public Optional<CoreItemChange> read(MatchTimelineResponse.Event event) {
        if (event.type() == null) {
            return Optional.empty();
        }
        return switch (event.type()) {
            case ITEM_PURCHASED -> Optional.of(new CoreItemChange(event.timestamp(), null, coreOrNull(event.itemId())));
            case ITEM_SOLD -> Optional.of(new CoreItemChange(event.timestamp(), coreOrNull(event.itemId()), null));
            case ITEM_UNDO -> Optional.of(new CoreItemChange(
                    event.timestamp(), coreOrNull(event.beforeId()), coreOrNull(event.afterId())));
            default -> Optional.empty();
        };
    }

    private Integer coreOrNull(Integer itemId) {
        if (itemId == null || !coreItemIds.contains(itemId)) {
            return null;
        }
        return itemId;
    }
}
