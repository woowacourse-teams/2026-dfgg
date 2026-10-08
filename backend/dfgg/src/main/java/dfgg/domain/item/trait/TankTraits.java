package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemProfile.self;
import static dfgg.domain.item.trait.ItemTrait.ABYSSAL_MASK;
import static dfgg.domain.item.trait.ItemTrait.DEAD_MAN_PLATE;
import static dfgg.domain.item.trait.ItemTrait.ENGAGE;
import static dfgg.domain.item.trait.ItemTrait.FORCE_OF_NATURE;
import static dfgg.domain.item.trait.ItemTrait.FROZEN_HEART;
import static dfgg.domain.item.trait.ItemTrait.HEARTSTEEL;
import static dfgg.domain.item.trait.ItemTrait.HOLLOW_RADIANCE;
import static dfgg.domain.item.trait.ItemTrait.ICEBORN_GAUNTLET;
import static dfgg.domain.item.trait.ItemTrait.JAKSHO_THE_PROTEAN;
import static dfgg.domain.item.trait.ItemTrait.KAENIC_ROOKERN;
import static dfgg.domain.item.trait.ItemTrait.PROTOPLASM_HARNESS;
import static dfgg.domain.item.trait.ItemTrait.RANDUIN_OMEN;
import static dfgg.domain.item.trait.ItemTrait.SPIRIT_VISAGE;
import static dfgg.domain.item.trait.ItemTrait.SUNFIRE_AEGIS;
import static dfgg.domain.item.trait.ItemTrait.THORNMAIL;
import static dfgg.domain.item.trait.ItemTrait.UNENDING_DESPAIR;
import static dfgg.domain.item.trait.ItemTrait.WARMOG_ARMOR;
import static dfgg.domain.item.trait.ItemTrait.WINTER_APPROACH;
import static dfgg.domain.item.trait.ItemTrait.ZEKE_CONVERGENCE;

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

    static final Map<Long, ItemProfile> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2502L, self(UNENDING_DESPAIR)),  // 끝없는 절망
            Map.entry(2504L, self(KAENIC_ROOKERN)),  // 케이닉 루컨
            Map.entry(2525L, self(PROTOPLASM_HARNESS)),  // 원형질 안전벨트
            Map.entry(3050L, self(ZEKE_CONVERGENCE)), // 지크의 융합
            Map.entry(3065L, self(SPIRIT_VISAGE)),  // 정령의 형상
            Map.entry(3068L, self(SUNFIRE_AEGIS)),  // 태양불꽃 방패
            Map.entry(3075L, self(THORNMAIL)),  // 가시 갑옷
            Map.entry(3083L, self(WARMOG_ARMOR)),  // 워모그의 갑옷
            Map.entry(3084L, self(HEARTSTEEL)),  // 강철심장
            Map.entry(3110L, self(FROZEN_HEART)),  // 얼어붙은 심장
            Map.entry(3119L, self(WINTER_APPROACH)),  // 혹한의 손길
            Map.entry(3143L, self(RANDUIN_OMEN)),  // 란두인의 예언
            Map.entry(3742L, self(DEAD_MAN_PLATE)),  // 망자의 갑옷
            Map.entry(4401L, self(FORCE_OF_NATURE)),  // 대자연의 힘
            Map.entry(6662L, self(ICEBORN_GAUNTLET)),  // 얼어붙은 건틀릿
            Map.entry(6664L, self(HOLLOW_RADIANCE)),  // 공허한 광휘
            Map.entry(6665L, self(JAKSHO_THE_PROTEAN)),  // 해신 작쇼
            Map.entry(8020L, self(ABYSSAL_MASK))   // 심연의 가면
    );

    private TankTraits() {
    }
}
