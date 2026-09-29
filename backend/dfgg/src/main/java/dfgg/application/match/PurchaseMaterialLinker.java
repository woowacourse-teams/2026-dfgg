package dfgg.application.match;

import dfgg.domain.match.ParticipantItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 구매 바로 앞의 같은 시각·프레임에 기록된 재료 제거 이벤트를 연결한다.
 * 빈 목록은 관측된 제거 재료가 없다는 뜻이고, 빈 Optional은 연결을 보장할 수 없다는 뜻이다.
 */
public class PurchaseMaterialLinker {

    public Optional<List<Integer>> link(ParticipantItemEvent purchase, List<ParticipantItemEvent> events) {
        if (purchase == null || !"ITEM_PURCHASED".equals(purchase.eventType()) || events == null) {
            throw new IllegalArgumentException("구매 이벤트와 참가자별 전체 아이템 이벤트가 필요합니다.");
        }
        int purchaseIndex = purchaseIndex(purchase, events);
        if (purchase.gameTimeMs() == null) {
            return Optional.empty();
        }
        List<Integer> materials = new ArrayList<>();
        for (int index = purchaseIndex - 1; index >= 0; index--) {
            ParticipantItemEvent event = events.get(index);
            if (!sameMoment(event, purchase)) {
                break;
            }
            if ("ITEM_PURCHASED".equals(event.eventType())) {
                break;
            }
            if (!"ITEM_DESTROYED".equals(event.eventType()) || event.itemId() == null) {
                return Optional.empty();
            }
            materials.add(event.itemId());
        }
        Collections.reverse(materials);
        return Optional.of(List.copyOf(materials));
    }

    private int purchaseIndex(ParticipantItemEvent purchase, List<ParticipantItemEvent> events) {
        int index = -1;
        int previousOrder = 0;
        for (int current = 0; current < events.size(); current++) {
            ParticipantItemEvent event = events.get(current);
            if (!purchase.matchId().equals(event.matchId()) || purchase.participantId() != event.participantId()
                    || event.eventOrder() != previousOrder + 1) {
                throw new IllegalArgumentException("한 참가자의 전체 아이템 이벤트를 eventOrder 순으로 전달해야 합니다.");
            }
            if (event.eventOrder() == purchase.eventOrder()) {
                if (!event.equals(purchase)) {
                    throw new IllegalArgumentException("구매 순번의 원천 이벤트가 입력과 다릅니다.");
                }
                index = current;
            }
            previousOrder = event.eventOrder();
        }
        if (index < 0) {
            throw new IllegalArgumentException("참가자 이벤트에서 대상 구매를 찾을 수 없습니다.");
        }
        return index;
    }

    private boolean sameMoment(ParticipantItemEvent event, ParticipantItemEvent purchase) {
        return event.sourceFrameIndex() == purchase.sourceFrameIndex()
                && purchase.gameTimeMs().equals(event.gameTimeMs());
    }
}
