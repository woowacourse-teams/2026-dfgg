package dfgg.application.match;

import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemPurchase;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 전체 행동은 Raw에서 읽고, 저장할 구매만 분리한다. Core 요약에는 영향을 주지 않는다. */
@Component
public class ParticipantItemPurchaseExtractor {

    private final ParticipantItemEventExtractor eventExtractor;

    public ParticipantItemPurchaseExtractor(ParticipantItemEventExtractor eventExtractor) {
        this.eventExtractor = eventExtractor;
    }

    /** purchaseTypes는 해당 패치의 카탈로그에 대해 서비스가 정한 분류다. 미등록을 OTHER로 추측하지 않는다. */
    public List<ParticipantItemPurchase> extract(
            String matchId, String rawTimeline, String patch, Map<Integer, ItemPurchaseType> purchaseTypes
    ) {
        if (purchaseTypes == null) {
            throw new IllegalArgumentException("패치별 구매 아이템 분류가 필요합니다.");
        }
        if (patch == null || patch.isBlank() || patch.length() > 16) {
            throw new IllegalArgumentException("패치는 비어 있지 않은 16자 이하의 문자열이어야 합니다.");
        }
        return eventExtractor.extract(matchId, rawTimeline).stream()
                .filter(event -> "ITEM_PURCHASED".equals(event.eventType()))
                .map(event -> {
                    ItemPurchaseType type = purchaseTypes.get(event.itemId());
                    if (type == null) {
                        throw new IllegalArgumentException("구매 아이템의 분류가 없습니다. 아이템 ID: " + event.itemId());
                    }
                    return new ParticipantItemPurchase(event, type, patch);
                })
                .toList();
    }
}
