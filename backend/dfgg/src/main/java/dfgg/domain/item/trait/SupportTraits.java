package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemTrait.MANA_REGEN_BASED_HEAL_SHIELD_AMPLIFY;
import static dfgg.domain.item.trait.ItemTrait.ALLY_OFFENSE_DEFENSE_BUFF;
import static dfgg.domain.item.trait.ItemTrait.AP_AND_ABILITY_HASTE_BUFF;
import static dfgg.domain.item.trait.ItemTrait.CC_ABILITY_HASTE_AND_ENEMY_VULNERABILITY;
import static dfgg.domain.item.trait.ItemTrait.DAMAGE_TO_HEAL;
import static dfgg.domain.item.trait.ItemTrait.DAMAGE_REDUCTION_AND_SLOW;
import static dfgg.domain.item.trait.ItemTrait.ALLY_SUSTAIN;
import static dfgg.domain.item.trait.ItemTrait.AOE_DAMAGE_DEFENSE;
import static dfgg.domain.item.trait.ItemTrait.AOE_HEAL;
import static dfgg.domain.item.trait.ItemTrait.ATTACK_SPEED_BUFF;
import static dfgg.domain.item.trait.ItemTrait.ATTACK_SPEED_AND_DAMAGE_BUFF;
import static dfgg.domain.item.trait.ItemTrait.CARRY_PROTECTION;
import static dfgg.domain.item.trait.ItemTrait.CC_CLEANSE;
import static dfgg.domain.item.trait.ItemTrait.ATTACK_BUFF_AND_ENEMY_VULNERABILITY;
import static dfgg.domain.item.trait.ItemTrait.HEAL_SHIELD_AMPLIFY;
import static dfgg.domain.item.trait.ItemTrait.SELF_HEAL_AND_MOBILITY_BUFF;
import static dfgg.domain.item.trait.ItemTrait.MOBILITY_BUFF;
import static dfgg.domain.item.trait.ItemTrait.SKILL_POKE;

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
            Map.entry(2065L, Set.of(MOBILITY_BUFF)), // 슈렐리아의 군가
            Map.entry(2524L, Set.of(ATTACK_SPEED_BUFF)),  // 밴들파이프
            Map.entry(2526L, Set.of(ALLY_SUSTAIN)),  // 속삭이는 머리띠
            Map.entry(3107L, Set.of(AOE_HEAL)), // 구원
            Map.entry(3109L, Set.of(CARRY_PROTECTION)), // 기사의 맹세
            Map.entry(3190L, Set.of(AOE_DAMAGE_DEFENSE)), // 강철의 솔라리 펜던트
            Map.entry(3222L, Set.of(CC_CLEANSE)), // 미카엘의 축복
            Map.entry(3504L, Set.of(ATTACK_SPEED_AND_DAMAGE_BUFF)), // 불타는 향로
            Map.entry(3869L, Set.of(DAMAGE_REDUCTION_AND_SLOW)), // 천상의 이의
            Map.entry(3870L, Set.of(ALLY_OFFENSE_DEFENSE_BUFF)), // 꿈 생성기
            Map.entry(3871L, Set.of(SKILL_POKE)),  // 자자크의 세계가시
            Map.entry(3876L, Set.of(SELF_HEAL_AND_MOBILITY_BUFF)), // 태양의 썰매
            Map.entry(3877L, Set.of(ATTACK_BUFF_AND_ENEMY_VULNERABILITY)),  // 피의 노래
            Map.entry(4005L, Set.of(CC_ABILITY_HASTE_AND_ENEMY_VULNERABILITY)), // 제국의 명령
            Map.entry(6616L, Set.of(AP_AND_ABILITY_HASTE_BUFF)), // 흐르는 물의 지팡이
            Map.entry(6617L, Set.of(HEAL_SHIELD_AMPLIFY)), // 월석 재생기
            Map.entry(6620L, Set.of(DAMAGE_TO_HEAL)), // 헬리아의 메아리
            Map.entry(6621L, Set.of(MANA_REGEN_BASED_HEAL_SHIELD_AMPLIFY)) // 새벽심장
    );

    private SupportTraits() {
    }
}
