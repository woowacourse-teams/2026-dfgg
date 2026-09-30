package dfgg.application.match;

import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemEvent;
import dfgg.domain.match.ParticipantItemPurchase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 전체 ITEM 행동은 Raw에서 읽고 모든 구매를 원천 순서대로 저장한다. Core 요약에는 영향을 주지 않는다. */
@Component
public class ParticipantItemPurchaseExtractor {

    private final ParticipantItemEventExtractor eventExtractor;

    public ParticipantItemPurchaseExtractor(ParticipantItemEventExtractor eventExtractor) {
        this.eventExtractor = eventExtractor;
    }

    /** purchaseTypes는 해당 패치의 카탈로그에 대해 서비스가 정한 분류다. 미등록을 OTHER로 추측하지 않는다. */
    public List<ParticipantItemPurchase> extract(
            String matchId, String rawTimeline, String patch, Map<Integer, ItemPurchaseType> purchaseTypes,
            Map<Integer, String> positionsByParticipant
    ) {
        validateInputs(patch, purchaseTypes);
        return extractFromEvents(eventExtractor.extract(matchId, rawTimeline), patch, purchaseTypes,
                positionsByParticipant);
    }

    /** 이미 추출한 전체 ITEM 이벤트에서 모든 구매를 원천 순서대로 만든다. */
    List<ParticipantItemPurchase> extractFromEvents(
            List<ParticipantItemEvent> events, String patch, Map<Integer, ItemPurchaseType> purchaseTypes,
            Map<Integer, String> positionsByParticipant
    ) {
        validateInputs(patch, purchaseTypes);
        if (events == null || positionsByParticipant == null) {
            throw new IllegalArgumentException("참가자별 전체 아이템 이벤트와 포지션이 필요합니다.");
        }
        List<ParticipantItemPurchase> purchases = new ArrayList<>();
        for (ParticipantItemEvent event : events) {
            if (!"ITEM_PURCHASED".equals(event.eventType())) {
                continue;
            }
            ItemPurchaseType type = purchaseTypes.get(event.itemId());
            if (type == null) {
                throw new IllegalArgumentException("구매 아이템의 분류가 없습니다. 아이템 ID: " + event.itemId());
            }
            if (!positionsByParticipant.containsKey(event.participantId())) {
                throw new IllegalArgumentException("구매 참가자의 포지션 정보가 없습니다. 참가자 ID: "
                        + event.participantId());
            }
            purchases.add(new ParticipantItemPurchase(event, type, patch,
                    positionsByParticipant.get(event.participantId())));
        }
        return List.copyOf(purchases);
    }

    private void validateInputs(String patch, Map<Integer, ItemPurchaseType> purchaseTypes) {
        if (purchaseTypes == null) {
            throw new IllegalArgumentException("패치별 구매 아이템 분류가 필요합니다.");
        }
        if (patch == null || patch.isBlank() || patch.length() > 16) {
            throw new IllegalArgumentException("패치는 비어 있지 않은 16자 이하의 문자열이어야 합니다.");
        }
    }
}
