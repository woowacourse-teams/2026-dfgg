package dfgg.domain.item;

import static dfgg.domain.item.ItemTrait.ENGAGE;
import static dfgg.domain.item.ItemTrait.HEAL;
import static dfgg.domain.item.ItemTrait.PEEL;
import static dfgg.domain.item.ItemTrait.SHIELD;
import static dfgg.domain.item.ItemTrait.TEAM_BUFF;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 아이템 ID에 수동으로 관리하는 trait를 연결한다.
 *
 * <p>수동 trait는 관측된 빌드를 분류하는 데만 사용하며 아이템 조합을 생성하지 않는다.
 */
public final class ItemTraitCatalog {

    private static final Map<Long, Set<ItemTrait>> DEFAULT_TRAITS = Map.ofEntries(
            Map.entry(2065L, Set.of(ENGAGE)), // 슈렐리아의 군가
            Map.entry(3050L, Set.of(ENGAGE)), // 지크의 융합
            Map.entry(3107L, Set.of(HEAL)), // 구원
            Map.entry(3109L, Set.of(PEEL)), // 기사의 맹세
            Map.entry(3190L, Set.of(PEEL, SHIELD)), // 강철의 솔라리 펜던트
            Map.entry(3222L, Set.of(PEEL, HEAL)), // 미카엘의 축복
            Map.entry(3504L, Set.of(TEAM_BUFF)), // 불타는 향로
            Map.entry(3869L, Set.of(PEEL)), // 천상의 이의
            Map.entry(3870L, Set.of(SHIELD, TEAM_BUFF)), // 꿈 생성기
            Map.entry(3876L, Set.of(HEAL, TEAM_BUFF)), // 태양의 썰매
            Map.entry(4005L, Set.of(TEAM_BUFF)), // 제국의 명령
            Map.entry(6616L, Set.of(TEAM_BUFF)), // 흐르는 물의 지팡이
            Map.entry(6617L, Set.of(HEAL, SHIELD)), // 월석 재생기
            Map.entry(6620L, Set.of(HEAL)), // 헬리아의 메아리
            Map.entry(6621L, Set.of(HEAL, SHIELD, TEAM_BUFF)) // 새벽심장
    );

    private final Map<Long, Set<ItemTrait>> traitsByItemId;

    public ItemTraitCatalog() {
        this(DEFAULT_TRAITS);
    }

    public ItemTraitCatalog(Map<Long, Set<ItemTrait>> traitsByItemId) {
        Objects.requireNonNull(traitsByItemId, "아이템 trait 목록은 null일 수 없습니다.");

        Map<Long, Set<ItemTrait>> copiedTraits = new HashMap<>();
        traitsByItemId.forEach((itemId, traits) -> {
            Objects.requireNonNull(itemId, "아이템 ID는 null일 수 없습니다.");
            Objects.requireNonNull(traits, "아이템 trait는 null일 수 없습니다.");
            copiedTraits.put(itemId, Set.copyOf(traits));
        });
        this.traitsByItemId = Map.copyOf(copiedTraits);
    }

    public Set<ItemTrait> traitsOf(Item item) {
        Objects.requireNonNull(item, "아이템은 null일 수 없습니다.");
        return traitsByItemId.getOrDefault(item.getItemId(), Set.of());
    }

    public boolean hasTrait(Item item, ItemTrait trait) {
        Objects.requireNonNull(trait, "아이템 trait는 null일 수 없습니다.");
        return traitsOf(item).contains(trait);
    }
}
