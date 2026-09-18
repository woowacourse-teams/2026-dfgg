package dfgg.domain.item.trait;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 마법사 아이템의 특성.
 * <p>
 * 아이템 하나가 여러 역할을 겸하면 여러 파일에 나뉘어 들어간다.
 * {@link ItemTraitCatalog}가 합쳐서 낸다 — 한쪽만 보면 가진 특성의 일부만 보인다.
 */
final class MageTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, Set<ItemTrait>> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2503L, Set.of()),  // 어둠불꽃 횃불
            Map.entry(2510L, Set.of()),  // 황혼과 새벽
            Map.entry(2522L, Set.of()),  // 실체화 장비
            Map.entry(3003L, Set.of()),  // 대천사의 지팡이
            Map.entry(3041L, Set.of()),  // 메자이의 영혼약탈자
            Map.entry(3089L, Set.of()),  // 라바돈의 죽음모자
            Map.entry(3100L, Set.of()),  // 리치베인
            Map.entry(3102L, Set.of()),  // 밴시의 장막
            Map.entry(3115L, Set.of()),  // 내셔의 이빨
            Map.entry(3116L, Set.of()),  // 라일라이의 수정홀
            Map.entry(3118L, Set.of()),  // 악의
            Map.entry(3135L, Set.of()),  // 공허의 지팡이
            Map.entry(3137L, Set.of()),  // 무덤꽃
            Map.entry(3146L, Set.of()),  // 마법공학 총검
            Map.entry(3152L, Set.of()),  // 마법공학 로켓 벨트
            Map.entry(3157L, Set.of()),  // 존야의 모래시계
            Map.entry(3165L, Set.of()),  // 모렐로노미콘
            Map.entry(4628L, Set.of()),  // 지평선의 초점
            Map.entry(4629L, Set.of()),  // 우주의 추진력
            Map.entry(4633L, Set.of()),  // 균열 생성기
            Map.entry(4645L, Set.of()),  // 그림자불꽃
            Map.entry(4646L, Set.of()),  // 폭풍 쇄도
            Map.entry(6653L, Set.of()),  // 리안드리의 고통
            Map.entry(6655L, Set.of()),  // 루덴의 메아리
            Map.entry(6657L, Set.of()),  // 영겁의 지팡이
            Map.entry(8010L, Set.of())   // 핏빛 저주
    );

    private MageTraits() {
    }
}
