package dfgg.domain.item.trait;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 암살자 아이템의 특성.
 * <p>
 * 아이템 하나가 여러 역할을 겸하면 여러 파일에 나뉘어 들어간다.
 * {@link ItemTraitCatalog}가 합쳐서 낸다 — 한쪽만 보면 가진 특성의 일부만 보인다.
 */
final class AssassinTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, Set<ItemTrait>> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2520L, Set.of()),  // 요새파괴자
            Map.entry(3142L, Set.of()),  // 요우무의 유령검
            Map.entry(3179L, Set.of()),  // 그림자 검
            Map.entry(3814L, Set.of()),  // 밤의 끝자락
            Map.entry(6694L, Set.of()),  // 세릴다의 원한
            Map.entry(6695L, Set.of()),  // 독사의 송곳니
            Map.entry(6696L, Set.of()),  // 원칙의 원형낫
            Map.entry(6697L, Set.of()),  // 오만
            Map.entry(6698L, Set.of()),  // 불경한 히드라
            Map.entry(6699L, Set.of())   // 벼락폭풍검
    );

    private AssassinTraits() {
    }
}
