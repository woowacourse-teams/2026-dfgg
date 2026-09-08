package dfgg.domain.item.trait;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 전사 아이템의 특성.
 * <p>
 * 아이템 하나가 여러 역할을 겸하면 여러 파일에 나뉘어 들어간다.
 * {@link ItemTraitCatalog}가 합쳐서 낸다 — 한쪽만 보면 가진 특성의 일부만 보인다.
 */
final class FighterTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, Set<ItemTrait>> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2501L, Set.of()),  // 지배자의 피갑옷
            Map.entry(2517L, Set.of()),  // 끝없는 갈망
            Map.entry(3004L, Set.of()),  // 마나무네
            Map.entry(3026L, Set.of()),  // 수호 천사
            Map.entry(3053L, Set.of()),  // 스테락의 도전
            Map.entry(3071L, Set.of()),  // 칠흑의 양날 도끼
            Map.entry(3073L, Set.of()),  // 실험적 마공학판
            Map.entry(3074L, Set.of()),  // 굶주린 히드라
            Map.entry(3078L, Set.of()),  // 삼위일체
            Map.entry(3091L, Set.of()),  // 마법사의 최후
            Map.entry(3153L, Set.of()),  // 몰락한 왕의 검
            Map.entry(3156L, Set.of()),  // 맬모셔스의 아귀
            Map.entry(3161L, Set.of()),  // 쇼진의 창
            Map.entry(3181L, Set.of()),  // 선체파괴자
            Map.entry(3748L, Set.of()),  // 거대한 히드라
            Map.entry(6333L, Set.of()),  // 죽음의 무도
            Map.entry(6609L, Set.of()),  // 화공 펑크 사슬검
            Map.entry(6610L, Set.of()),  // 갈라진 하늘
            Map.entry(6631L, Set.of()),  // 발걸음 분쇄기
            Map.entry(6692L, Set.of())   // 월식
    );

    private FighterTraits() {
    }
}
