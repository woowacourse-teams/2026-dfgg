package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemProfile.self;
import static dfgg.domain.item.trait.ItemTrait.BERSERKER_GREAVES;
import static dfgg.domain.item.trait.ItemTrait.BOOTS_OF_SWIFTNESS;
import static dfgg.domain.item.trait.ItemTrait.IONIAN_BOOTS_OF_LUCIDITY;
import static dfgg.domain.item.trait.ItemTrait.MERCURY_TREADS;
import static dfgg.domain.item.trait.ItemTrait.PLATED_STEELCAPS;
import static dfgg.domain.item.trait.ItemTrait.SORCERER_SHOES;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 신발의 특성.
 * <p>
 * 역할이 아니라 슬롯으로 묶는다 — 신발은 어느 역할이든 하나씩 산다.
 */
final class BootsTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, ItemProfile> BY_ITEM_ID = Map.ofEntries(
            Map.entry(3006L, self(BERSERKER_GREAVES)),  // 광전사의 군화
            Map.entry(3009L, self(BOOTS_OF_SWIFTNESS)),  // 신속의 장화
            Map.entry(3020L, self(SORCERER_SHOES)),  // 마법사의 신발
            Map.entry(3047L, self(PLATED_STEELCAPS)),  // 판금 장화
            Map.entry(3111L, self(MERCURY_TREADS)),  // 헤르메스의 발걸음
            Map.entry(3158L, self(IONIAN_BOOTS_OF_LUCIDITY))   // 명석함의 아이오니아 장화
    );

    private BootsTraits() {
    }
}
