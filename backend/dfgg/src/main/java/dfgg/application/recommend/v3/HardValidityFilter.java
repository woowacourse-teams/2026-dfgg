package dfgg.application.recommend.v3;

import dfgg.domain.item.Item;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 게임 규칙상 <b>살 수 없는</b> 아이템만 제거한다.
 *
 * <p>여기서 하지 않는 판단이 무엇인지가 더 중요하다 — AD/AP 궁합, counter 적합성, 메타 선호,
 * 챔피언 선호는 전부 걸러내지 않는다. AD 르블랑 같은 비정형 빌드가 패치마다 생기기 때문에
 * 정체성 기반 hard filter는 정답을 지워버린다. 그 trade-off는 LTR이 학습한다.
 *
 * <p>제거 대상은 셋뿐이다: 이미 보유한 아이템, 신발을 신고 있을 때의 다른 신발,
 * 그리고 아이템 메타데이터가 없어 응답으로 만들 수 없는 후보.
 */
@Component
public class HardValidityFilter {

    private static final String BOOTS_TAG = "Boots";

    public CandidateUnion filter(
            CandidateUnion union,
            List<Long> purchasedItemIds,
            Map<Long, Item> itemById
    ) {
        Set<Long> purchased = Set.copyOf(purchasedItemIds);
        boolean bootsAlreadyOwned = hasBoots(purchasedItemIds, itemById);

        List<ItemCandidate> valid = union.candidates().stream()
                .filter(candidate -> isPurchasable(candidate, purchased, bootsAlreadyOwned, itemById))
                .toList();
        return new CandidateUnion(valid);
    }

    private boolean isPurchasable(
            ItemCandidate candidate,
            Set<Long> purchasedItemIds,
            boolean bootsAlreadyOwned,
            Map<Long, Item> itemById
    ) {
        Item item = itemById.get(candidate.itemId());
        if (item == null) {
            return false;
        }
        if (purchasedItemIds.contains(candidate.itemId())) {
            return false;
        }
        return !bootsAlreadyOwned || !item.hasTag(BOOTS_TAG);
    }

    private boolean hasBoots(List<Long> purchasedItemIds, Map<Long, Item> itemById) {
        return purchasedItemIds.stream()
                .map(itemById::get)
                .anyMatch(item -> item != null && item.hasTag(BOOTS_TAG));
    }
}
