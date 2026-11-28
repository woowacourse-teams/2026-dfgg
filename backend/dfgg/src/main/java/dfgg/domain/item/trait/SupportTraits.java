package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemProfile.ally;
import static dfgg.domain.item.trait.ItemProfile.self;
import static dfgg.domain.item.trait.ItemTrait.DAWNCORE;
import static dfgg.domain.item.trait.ItemTrait.DREAM_MAKER;
import static dfgg.domain.item.trait.ItemTrait.STAFF_OF_FLOWING_WATER;
import static dfgg.domain.item.trait.ItemTrait.IMPERIAL_MANDATE;
import static dfgg.domain.item.trait.ItemTrait.ECHOES_OF_HELIA;
import static dfgg.domain.item.trait.ItemTrait.CELESTIAL_OPPOSITION;
import static dfgg.domain.item.trait.ItemTrait.WHISPERING_CIRCLET;
import static dfgg.domain.item.trait.ItemTrait.LOCKET_OF_THE_IRON_SOLARI;
import static dfgg.domain.item.trait.ItemTrait.REDEMPTION;
import static dfgg.domain.item.trait.ItemTrait.BANDLEPIPES;
import static dfgg.domain.item.trait.ItemTrait.ARDENT_CENSER;
import static dfgg.domain.item.trait.ItemTrait.KNIGHT_VOW;
import static dfgg.domain.item.trait.ItemTrait.MIKAEL_BLESSING;
import static dfgg.domain.item.trait.ItemTrait.BLOODSONG;
import static dfgg.domain.item.trait.ItemTrait.MOONSTONE_RENEWER;
import static dfgg.domain.item.trait.ItemTrait.SOLSTICE_SLEIGH;
import static dfgg.domain.item.trait.ItemTrait.SHURELYA_BATTLESONG;
import static dfgg.domain.item.trait.ItemTrait.ZAZZAK_REALMSPIKE;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 서포터 아이템의 특성과 synergy.
 * <p>
 * {@code ally(...)}는 아군에게 작용하는 아이템, {@code self(...)}는 자기에게만 작용하는 아이템이다.
 * 서포터가 사도 자기에게만 작용하면 {@code self}다 — 기준은 포지션이 아니라 아이템이다.
 * <p>
 * 아이템 하나가 여러 역할을 겸하면 여러 파일에 나뉘어 들어간다.
 * {@link ItemTraitCatalog}가 합쳐서 낸다 — 한쪽만 보면 가진 특성의 일부만 보인다.
 */
final class SupportTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, ItemProfile> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2065L, ally(SHURELYA_BATTLESONG)), // 슈렐리아의 군가
            Map.entry(2524L, ally(BANDLEPIPES)),  // 밴들파이프
            Map.entry(2526L, ally(WHISPERING_CIRCLET)),  // 속삭이는 머리띠
            Map.entry(3107L, ally(REDEMPTION)), // 구원
            Map.entry(3109L, ally(KNIGHT_VOW)), // 기사의 맹세
            Map.entry(3190L, ally(LOCKET_OF_THE_IRON_SOLARI)), // 강철의 솔라리 펜던트
            Map.entry(3222L, ally(MIKAEL_BLESSING)), // 미카엘의 축복
            Map.entry(3504L, ally(ARDENT_CENSER)), // 불타는 향로
            Map.entry(3869L, self(CELESTIAL_OPPOSITION)), // 천상의 이의
            Map.entry(3870L, ally(DREAM_MAKER)), // 꿈 생성기
            Map.entry(3871L, self(ZAZZAK_REALMSPIKE)),  // 자자크의 세계가시
            Map.entry(3876L, ally(SOLSTICE_SLEIGH)), // 태양의 썰매
            Map.entry(3877L, ally(BLOODSONG)),  // 피의 노래
            Map.entry(4005L, ally(IMPERIAL_MANDATE)), // 제국의 명령
            Map.entry(6616L, ally(STAFF_OF_FLOWING_WATER)), // 흐르는 물의 지팡이
            Map.entry(6617L, ally(MOONSTONE_RENEWER)), // 월석 재생기
            Map.entry(6620L, ally(ECHOES_OF_HELIA)), // 헬리아의 메아리
            Map.entry(6621L, self(DAWNCORE)) // 새벽심장
    );

    private SupportTraits() {
    }
}
