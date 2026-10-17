package dfgg.application.recommend.v3;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemExclusionGroups;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 게임 규칙상 <b>살 수 없는</b> 아이템만 제거한다.
 *
 * <p>여기서 하지 않는 판단이 더 중요하다 — AD/AP 궁합, counter 적합성, 메타 선호, 챔피언 선호는
 * 전부 걸러내지 않는다. AD 르블랑 같은 비정형 빌드가 패치마다 생기기 때문에 정체성 기반
 * hard filter는 정답을 지워버린다. 그 trade-off는 LTR이 학습한다.
 *
 * <p>제거 대상은 셋뿐이다: 이미 보유한 아이템, 보유 아이템과 동시에 가질 수 없는 아이템
 * (신발끼리, 고유 효과가 겹치는 조합), 그리고 메타데이터가 없어 응답으로 만들 수 없는 후보.
 */
@Component
public class HardValidityFilter {

    private final ItemExclusionGroups exclusionGroups;

    public HardValidityFilter(ItemExclusionGroups exclusionGroups) {
        this.exclusionGroups = exclusionGroups;
    }

    public CandidateUnion filter(
            CandidateUnion union,
            List<Long> purchasedItemIds,
            Map<Long, Item> itemById
    ) {
        Set<Long> purchased = Set.copyOf(purchasedItemIds);
        List<Item> ownedItems = purchasedItemIds.stream()
                .map(itemById::get)
                .filter(Objects::nonNull)
                .toList();

        List<ItemCandidate> valid = union.candidates().stream()
                .filter(candidate -> isPurchasable(candidate, purchased, ownedItems, itemById))
                .toList();
        return new CandidateUnion(valid);
    }

    private boolean isPurchasable(
            ItemCandidate candidate,
            Set<Long> purchasedItemIds,
            List<Item> ownedItems,
            Map<Long, Item> itemById
    ) {
        Item item = itemById.get(candidate.itemId());
        if (item == null) {
            return false;
        }
        if (purchasedItemIds.contains(candidate.itemId())) {
            return false;
        }
        return ownedItems.stream().noneMatch(owned -> exclusionGroups.isExclusiveWith(owned, item));
    }
}
