package dfgg.domain.item.trait;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 탱커 아이템의 특성.
 * <p>
 * 아이템 하나가 여러 역할을 겸하면 여러 파일에 나뉘어 들어간다.
 * {@link ItemTraitCatalog}가 합쳐서 낸다 — 한쪽만 보면 가진 특성의 일부만 보인다.
 */
final class TankTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, Set<ItemTrait>> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2502L, Set.of()),  // 끝없는 절망
            Map.entry(2504L, Set.of()),  // 케이닉 루컨
            Map.entry(2525L, Set.of()),  // 원형질 안전벨트
            Map.entry(3050L, Set.of()),  // 지크의 융합
            Map.entry(3065L, Set.of()),  // 정령의 형상
            Map.entry(3068L, Set.of()),  // 태양불꽃 방패
            Map.entry(3075L, Set.of()),  // 가시 갑옷
            Map.entry(3083L, Set.of()),  // 워모그의 갑옷
            Map.entry(3084L, Set.of()),  // 강철심장
            Map.entry(3110L, Set.of()),  // 얼어붙은 심장
            Map.entry(3119L, Set.of()),  // 혹한의 손길
            Map.entry(3143L, Set.of()),  // 란두인의 예언
            Map.entry(3742L, Set.of()),  // 망자의 갑옷
            Map.entry(4401L, Set.of()),  // 대자연의 힘
            Map.entry(6662L, Set.of()),  // 얼어붙은 건틀릿
            Map.entry(6664L, Set.of()),  // 공허한 광휘
            Map.entry(6665L, Set.of()),  // 해신 작쇼
            Map.entry(8020L, Set.of())   // 심연의 가면
    );

    private TankTraits() {
    }
}
