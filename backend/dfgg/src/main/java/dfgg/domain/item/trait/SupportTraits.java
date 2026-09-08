package dfgg.domain.item.trait;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 서포터 아이템의 특성.
 * <p>
 * 아이템 하나가 여러 역할을 겸하면 여러 파일에 나뉘어 들어간다.
 * {@link ItemTraitCatalog}가 합쳐서 낸다 — 한쪽만 보면 가진 특성의 일부만 보인다.
 */
final class SupportTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, Set<ItemTrait>> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2065L, Set.of()),  // 슈렐리아의 군가
            Map.entry(2524L, Set.of()),  // 밴들파이프
            Map.entry(2526L, Set.of()),  // 속삭이는 머리띠
            Map.entry(3107L, Set.of()),  // 구원
            Map.entry(3109L, Set.of()),  // 기사의 맹세
            Map.entry(3190L, Set.of()),  // 강철의 솔라리 펜던트
            Map.entry(3222L, Set.of()),  // 미카엘의 축복
            Map.entry(3504L, Set.of()),  // 불타는 향로
            Map.entry(3869L, Set.of()),  // 천상의 이의
            Map.entry(3870L, Set.of()),  // 꿈 생성기
            Map.entry(3871L, Set.of()),  // 자자크의 세계가시
            Map.entry(3876L, Set.of()),  // 태양의 썰매
            Map.entry(3877L, Set.of()),  // 피의 노래
            Map.entry(4005L, Set.of()),  // 제국의 명령
            Map.entry(6616L, Set.of()),  // 흐르는 물의 지팡이
            Map.entry(6617L, Set.of()),  // 월석 재생기
            Map.entry(6620L, Set.of()),  // 헬리아의 메아리
            Map.entry(6621L, Set.of())   // 새벽심장
    );

    private SupportTraits() {
    }
}
