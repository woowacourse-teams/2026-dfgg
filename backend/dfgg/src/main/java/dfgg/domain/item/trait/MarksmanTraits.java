package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemProfile.self;
import static dfgg.domain.item.trait.ItemTrait.BLOODTHIRSTER;
import static dfgg.domain.item.trait.ItemTrait.ESSENCE_REAVER;
import static dfgg.domain.item.trait.ItemTrait.FIENDHUNTER_BOLTS;
import static dfgg.domain.item.trait.ItemTrait.GUINSOO_RAGEBLADE;
import static dfgg.domain.item.trait.ItemTrait.HEXOPTICS_C44;
import static dfgg.domain.item.trait.ItemTrait.IMMORTAL_SHIELDBOW;
import static dfgg.domain.item.trait.ItemTrait.INFINITY_EDGE;
import static dfgg.domain.item.trait.ItemTrait.KRAKEN_SLAYER;
import static dfgg.domain.item.trait.ItemTrait.LORD_DOMINIK_REGARDS;
import static dfgg.domain.item.trait.ItemTrait.MERCURIAL_SCIMITAR;
import static dfgg.domain.item.trait.ItemTrait.MORTAL_REMINDER;
import static dfgg.domain.item.trait.ItemTrait.NAVORI_FLICKERBLADE;
import static dfgg.domain.item.trait.ItemTrait.PHANTOM_DANCER;
import static dfgg.domain.item.trait.ItemTrait.RAPID_FIRECANNON;
import static dfgg.domain.item.trait.ItemTrait.RUNAAN_HURRICANE;
import static dfgg.domain.item.trait.ItemTrait.STATIKK_SHIV;
import static dfgg.domain.item.trait.ItemTrait.STORMRAZOR;
import static dfgg.domain.item.trait.ItemTrait.TERMINUS;
import static dfgg.domain.item.trait.ItemTrait.THE_COLLECTOR;
import static dfgg.domain.item.trait.ItemTrait.YUN_TAL_WILDARROWS;

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

    static final Map<Long, ItemProfile> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2512L, self(FIENDHUNTER_BOLTS)),  // 악마사냥꾼의 화살
            Map.entry(2523L, self(HEXOPTICS_C44)),  // 마법광학 장치 C44
            Map.entry(3031L, self(INFINITY_EDGE)),  // 무한의 대검
            Map.entry(3032L, self(YUN_TAL_WILDARROWS)),  // 윤 탈 야생화살
            Map.entry(3033L, self(MORTAL_REMINDER)),  // 필멸자의 운명
            Map.entry(3036L, self(LORD_DOMINIK_REGARDS)),  // 도미닉 경의 인사
            Map.entry(3046L, self(PHANTOM_DANCER)),  // 유령 무희
            Map.entry(3072L, self(BLOODTHIRSTER)),  // 피바라기
            Map.entry(3085L, self(RUNAAN_HURRICANE)),  // 루난의 허리케인
            Map.entry(3087L, self(STATIKK_SHIV)),  // 스태틱의 단검
            Map.entry(3094L, self(RAPID_FIRECANNON)),  // 고속 연사포
            Map.entry(3095L, self(STORMRAZOR)),  // 폭풍갈퀴 (16.17~)
            Map.entry(3097L, self(STORMRAZOR)),  // 폭풍갈퀴 (~16.16)
            Map.entry(3124L, self(GUINSOO_RAGEBLADE)),  // 구인수의 격노검
            Map.entry(3139L, self(MERCURIAL_SCIMITAR)),  // 헤르메스의 시미터
            Map.entry(3302L, self(TERMINUS)),  // 경계
            Map.entry(3508L, self(ESSENCE_REAVER)),  // 정수 약탈자
            Map.entry(6672L, self(KRAKEN_SLAYER)),  // 크라켄 학살자
            Map.entry(6673L, self(IMMORTAL_SHIELDBOW)),  // 불멸의 철갑궁
            Map.entry(6675L, self(NAVORI_FLICKERBLADE)),  // 나보리 명멸검
            Map.entry(6676L, self(THE_COLLECTOR))   // 징수의 총
    );

    private MarksmanTraits() {
    }
}
