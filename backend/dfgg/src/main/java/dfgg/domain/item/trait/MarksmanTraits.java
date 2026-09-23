package dfgg.domain.item.trait;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 원거리 딜러 아이템의 특성.
 * <p>
 * 아이템 하나가 여러 역할을 겸하면 여러 파일에 나뉘어 들어간다.
 * {@link ItemTraitCatalog}가 합쳐서 낸다 — 한쪽만 보면 가진 특성의 일부만 보인다.
 */
final class MarksmanTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, Set<ItemTrait>> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2512L, Set.of()),  // 악마사냥꾼의 화살
            Map.entry(2523L, Set.of()),  // 마법광학 장치 C44
            Map.entry(3031L, Set.of()),  // 무한의 대검
            Map.entry(3032L, Set.of()),  // 윤 탈 야생화살
            Map.entry(3033L, Set.of()),  // 필멸자의 운명
            Map.entry(3036L, Set.of()),  // 도미닉 경의 인사
            Map.entry(3046L, Set.of()),  // 유령 무희
            Map.entry(3072L, Set.of()),  // 피바라기
            Map.entry(3085L, Set.of()),  // 루난의 허리케인
            Map.entry(3087L, Set.of()),  // 스태틱의 단검
            Map.entry(3094L, Set.of()),  // 고속 연사포
            Map.entry(3097L, Set.of()),  // 폭풍갈퀴
            Map.entry(3124L, Set.of()),  // 구인수의 격노검
            Map.entry(3139L, Set.of()),  // 헤르메스의 시미터
            Map.entry(3302L, Set.of()),  // 경계
            Map.entry(3508L, Set.of()),  // 정수 약탈자
            Map.entry(6672L, Set.of()),  // 크라켄 학살자
            Map.entry(6673L, Set.of()),  // 불멸의 철갑궁
            Map.entry(6675L, Set.of()),  // 나보리 명멸검
            Map.entry(6676L, Set.of())   // 징수의 총
    );

    private MarksmanTraits() {
    }
}
