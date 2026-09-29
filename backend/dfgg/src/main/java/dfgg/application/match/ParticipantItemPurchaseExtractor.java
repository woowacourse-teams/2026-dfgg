package dfgg.application.match;

import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemPurchase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 전체 행동은 Raw에서 읽고, Component 추천 대상 구매만 저장한다. Core 요약에는 영향을 주지 않는다. */
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
        List<ParticipantItemPurchase> purchases = new ArrayList<>();
        for (var event : eventExtractor.extract(matchId, rawTimeline)) {
            if (!"ITEM_PURCHASED".equals(event.eventType())) {
                continue;
            }
            ItemPurchaseType type = purchaseTypes.get(event.itemId());
            if (type == null) {
                throw new IllegalArgumentException("구매 아이템의 분류가 없습니다. 아이템 ID: " + event.itemId());
            }
            if (type.isComponentRecommendationTarget()) {
                // 제외된 구매도 원천 purchaseOrder를 차지하므로 순번은 다시 매기지 않는다.
                purchases.add(new ParticipantItemPurchase(event, type, patch));
            }
        }
        return List.copyOf(purchases);
    }
}
