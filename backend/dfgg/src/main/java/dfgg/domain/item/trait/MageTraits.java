package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemProfile.self;
import static dfgg.domain.item.trait.ItemTrait.ACTUALIZER;
import static dfgg.domain.item.trait.ItemTrait.ARCHANGEL_STAFF;
import static dfgg.domain.item.trait.ItemTrait.BANSHEE_VEIL;
import static dfgg.domain.item.trait.ItemTrait.BLACKFIRE_TORCH;
import static dfgg.domain.item.trait.ItemTrait.BLOODLETTER_CURSE;
import static dfgg.domain.item.trait.ItemTrait.COSMIC_DRIVE;
import static dfgg.domain.item.trait.ItemTrait.CRYPTBLOOM;
import static dfgg.domain.item.trait.ItemTrait.DUSK_AND_DAWN;
import static dfgg.domain.item.trait.ItemTrait.HEXTECH_GUNBLADE;
import static dfgg.domain.item.trait.ItemTrait.HEXTECH_ROCKETBELT;
import static dfgg.domain.item.trait.ItemTrait.HORIZON_FOCUS;
import static dfgg.domain.item.trait.ItemTrait.LIANDRY_TORMENT;
import static dfgg.domain.item.trait.ItemTrait.LICH_BANE;
import static dfgg.domain.item.trait.ItemTrait.LUDEN_ECHO;
import static dfgg.domain.item.trait.ItemTrait.MALIGNANCE;
import static dfgg.domain.item.trait.ItemTrait.MEJAI_SOULSTEALER;
import static dfgg.domain.item.trait.ItemTrait.MORELLONOMICON;
import static dfgg.domain.item.trait.ItemTrait.NASHOR_TOOTH;
import static dfgg.domain.item.trait.ItemTrait.RABADON_DEATHCAP;
import static dfgg.domain.item.trait.ItemTrait.RIFTMAKER;
import static dfgg.domain.item.trait.ItemTrait.ROD_OF_AGES;
import static dfgg.domain.item.trait.ItemTrait.RYLAI_CRYSTAL_SCEPTER;
import static dfgg.domain.item.trait.ItemTrait.SHADOWFLAME;
import static dfgg.domain.item.trait.ItemTrait.STORMSURGE;
import static dfgg.domain.item.trait.ItemTrait.VOID_STAFF;
import static dfgg.domain.item.trait.ItemTrait.ZHONYA_HOURGLASS;

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

    static final Map<Long, ItemProfile> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2503L, self(BLACKFIRE_TORCH)),  // 어둠불꽃 횃불
            Map.entry(2510L, self(DUSK_AND_DAWN)),  // 황혼과 새벽
            Map.entry(2522L, self(ACTUALIZER)),  // 실체화 장비
            Map.entry(3003L, self(ARCHANGEL_STAFF)),  // 대천사의 지팡이
            Map.entry(3041L, self(MEJAI_SOULSTEALER)),  // 메자이의 영혼약탈자
            Map.entry(3089L, self(RABADON_DEATHCAP)),  // 라바돈의 죽음모자
            Map.entry(3100L, self(LICH_BANE)),  // 리치베인
            Map.entry(3102L, self(BANSHEE_VEIL)),  // 밴시의 장막
            Map.entry(3115L, self(NASHOR_TOOTH)),  // 내셔의 이빨
            Map.entry(3116L, self(RYLAI_CRYSTAL_SCEPTER)),  // 라일라이의 수정홀
            Map.entry(3118L, self(MALIGNANCE)),  // 악의
            Map.entry(3135L, self(VOID_STAFF)),  // 공허의 지팡이
            Map.entry(3137L, self(CRYPTBLOOM)),  // 무덤꽃
            Map.entry(3146L, self(HEXTECH_GUNBLADE)),  // 마법공학 총검
            Map.entry(3152L, self(HEXTECH_ROCKETBELT)),  // 마법공학 로켓 벨트
            Map.entry(3157L, self(ZHONYA_HOURGLASS)),  // 존야의 모래시계
            Map.entry(3165L, self(MORELLONOMICON)),  // 모렐로노미콘
            Map.entry(4628L, self(HORIZON_FOCUS)),  // 지평선의 초점
            Map.entry(4629L, self(COSMIC_DRIVE)),  // 우주의 추진력
            Map.entry(4633L, self(RIFTMAKER)),  // 균열 생성기
            Map.entry(4645L, self(SHADOWFLAME)),  // 그림자불꽃
            Map.entry(4646L, self(STORMSURGE)),  // 폭풍 쇄도
            Map.entry(6653L, self(LIANDRY_TORMENT)),  // 리안드리의 고통
            Map.entry(6655L, self(LUDEN_ECHO)),  // 루덴의 메아리
            Map.entry(6657L, self(ROD_OF_AGES)),  // 영겁의 지팡이
            Map.entry(8010L, self(BLOODLETTER_CURSE))   // 핏빛 저주
    );

    private MageTraits() {
    }
}
