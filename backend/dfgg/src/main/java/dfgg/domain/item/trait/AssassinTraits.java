package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemProfile.self;
import static dfgg.domain.item.trait.ItemTrait.AXIOM_ARC;
import static dfgg.domain.item.trait.ItemTrait.BASTIONBREAKER;
import static dfgg.domain.item.trait.ItemTrait.EDGE_OF_NIGHT;
import static dfgg.domain.item.trait.ItemTrait.HUBRIS;
import static dfgg.domain.item.trait.ItemTrait.PROFANE_HYDRA;
import static dfgg.domain.item.trait.ItemTrait.SERPENT_FANG;
import static dfgg.domain.item.trait.ItemTrait.SERYLDA_GRUDGE;
import static dfgg.domain.item.trait.ItemTrait.UMBRAL_GLAIVE;
import static dfgg.domain.item.trait.ItemTrait.VOLTAIC_CYCLOSWORD;
import static dfgg.domain.item.trait.ItemTrait.YOUMUU_GHOSTBLADE;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 암살자 아이템의 특성.
 * <p>
 * 아이템 하나가 여러 역할을 겸하면 여러 파일에 나뉘어 들어간다.
 * {@link ItemTraitCatalog}가 합쳐서 낸다 — 한쪽만 보면 가진 특성의 일부만 보인다.
 */
final class AssassinTraits {

    static final Set<ItemTrait> CATEGORY = EnumSet.noneOf(ItemTrait.class);

    static final Map<Long, ItemProfile> BY_ITEM_ID = Map.ofEntries(
            Map.entry(2520L, self(BASTIONBREAKER)),  // 요새파괴자
            Map.entry(3142L, self(YOUMUU_GHOSTBLADE)),  // 요우무의 유령검
            Map.entry(3179L, self(UMBRAL_GLAIVE)),  // 그림자 검
            Map.entry(3814L, self(EDGE_OF_NIGHT)),  // 밤의 끝자락
            Map.entry(6694L, self(SERYLDA_GRUDGE)),  // 세릴다의 원한
            Map.entry(6695L, self(SERPENT_FANG)),  // 독사의 송곳니
            Map.entry(6696L, self(AXIOM_ARC)),  // 원칙의 원형낫
            Map.entry(6697L, self(HUBRIS)),  // 오만
            Map.entry(6698L, self(PROFANE_HYDRA)),  // 불경한 히드라
            Map.entry(6699L, self(VOLTAIC_CYCLOSWORD))   // 벼락폭풍검
    );

    private AssassinTraits() {
    }
}
