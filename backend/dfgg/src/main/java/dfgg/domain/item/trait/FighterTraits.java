package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemProfile.self;
import static dfgg.domain.item.trait.ItemTrait.BLACK_CLEAVER;
import static dfgg.domain.item.trait.ItemTrait.BLADE_OF_THE_RUINED_KING;
import static dfgg.domain.item.trait.ItemTrait.CHEMPUNK_CHAINSWORD;
import static dfgg.domain.item.trait.ItemTrait.DEATH_DANCE;
import static dfgg.domain.item.trait.ItemTrait.ECLIPSE;
import static dfgg.domain.item.trait.ItemTrait.ENDLESS_HUNGER;
import static dfgg.domain.item.trait.ItemTrait.EXPERIMENTAL_HEXPLATE;
import static dfgg.domain.item.trait.ItemTrait.GUARDIAN_ANGEL;
import static dfgg.domain.item.trait.ItemTrait.HULLBREAKER;
import static dfgg.domain.item.trait.ItemTrait.MANAMUNE;
import static dfgg.domain.item.trait.ItemTrait.MAW_OF_MALMORTIUS;
import static dfgg.domain.item.trait.ItemTrait.OVERLORD_BLOODMAIL;
import static dfgg.domain.item.trait.ItemTrait.RAVENOUS_HYDRA;
import static dfgg.domain.item.trait.ItemTrait.SPEAR_OF_SHOJIN;
import static dfgg.domain.item.trait.ItemTrait.STERAK_GAGE;
import static dfgg.domain.item.trait.ItemTrait.STRIDEBREAKER;
import static dfgg.domain.item.trait.ItemTrait.SUNDERED_SKY;
import static dfgg.domain.item.trait.ItemTrait.TITANIC_HYDRA;
import static dfgg.domain.item.trait.ItemTrait.TRINITY_FORCE;
import static dfgg.domain.item.trait.ItemTrait.WIT_END;

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

    static final Map<Long, ItemProfile> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2501L, self(OVERLORD_BLOODMAIL)),  // 지배자의 피갑옷
            Map.entry(2517L, self(ENDLESS_HUNGER)),  // 끝없는 갈망
            Map.entry(3004L, self(MANAMUNE)),  // 마나무네
            Map.entry(3026L, self(GUARDIAN_ANGEL)),  // 수호 천사
            Map.entry(3053L, self(STERAK_GAGE)),  // 스테락의 도전
            Map.entry(3071L, self(BLACK_CLEAVER)),  // 칠흑의 양날 도끼
            Map.entry(3073L, self(EXPERIMENTAL_HEXPLATE)),  // 실험적 마공학판
            Map.entry(3074L, self(RAVENOUS_HYDRA)),  // 굶주린 히드라
            Map.entry(3078L, self(TRINITY_FORCE)),  // 삼위일체
            Map.entry(3091L, self(WIT_END)),  // 마법사의 최후
            Map.entry(3153L, self(BLADE_OF_THE_RUINED_KING)),  // 몰락한 왕의 검
            Map.entry(3156L, self(MAW_OF_MALMORTIUS)),  // 맬모셔스의 아귀
            Map.entry(3161L, self(SPEAR_OF_SHOJIN)),  // 쇼진의 창
            Map.entry(3181L, self(HULLBREAKER)),  // 선체파괴자
            Map.entry(3748L, self(TITANIC_HYDRA)),  // 거대한 히드라
            Map.entry(6333L, self(DEATH_DANCE)),  // 죽음의 무도
            Map.entry(6609L, self(CHEMPUNK_CHAINSWORD)),  // 화공 펑크 사슬검
            Map.entry(6610L, self(SUNDERED_SKY)),  // 갈라진 하늘
            Map.entry(6631L, self(STRIDEBREAKER)),  // 발걸음 분쇄기
            Map.entry(6692L, self(ECLIPSE))   // 월식
    );

    private FighterTraits() {
    }
}
