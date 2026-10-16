package dfgg.domain.item.trait;

import dfgg.domain.item.Item;


import java.util.EnumSet;
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

    /**
     * 일곱 파일을 합친 기본 어휘.
     */
    private static final Map<Long, Set<ItemTrait>> DEFAULT_TRAITS = merge(
            ItemProfile.traitsByItemId(FighterTraits.BY_ITEM_ID),
            ItemProfile.traitsByItemId(MarksmanTraits.BY_ITEM_ID),
            ItemProfile.traitsByItemId(AssassinTraits.BY_ITEM_ID),
            ItemProfile.traitsByItemId(MageTraits.BY_ITEM_ID),
            ItemProfile.traitsByItemId(TankTraits.BY_ITEM_ID),
            ItemProfile.traitsByItemId(SupportTraits.BY_ITEM_ID),
            ItemProfile.traitsByItemId(BootsTraits.BY_ITEM_ID)
    );

    /**
     * synergy를 선언한 파일만 모은다. 선언하지 않은 아이템은 {@link Synergy#SELF}다.
     */
    private static final Map<Long, Synergy> DEFAULT_SYNERGIES = mergeSynergies(
            FighterTraits.BY_ITEM_ID, MarksmanTraits.BY_ITEM_ID, AssassinTraits.BY_ITEM_ID,
            MageTraits.BY_ITEM_ID, TankTraits.BY_ITEM_ID, SupportTraits.BY_ITEM_ID, BootsTraits.BY_ITEM_ID
    );

    @SafeVarargs
    private static Map<Long, Set<ItemTrait>> merge(Map<Long, Set<ItemTrait>>... sources) {
        Map<Long, Set<ItemTrait>> merged = new HashMap<>();
        for (Map<Long, Set<ItemTrait>> source : sources) {
            source.forEach((itemId, traits) -> merged
                    .computeIfAbsent(itemId, id -> EnumSet.noneOf(ItemTrait.class))
                    .addAll(traits));
        }
        merged.replaceAll((itemId, traits) -> Set.copyOf(traits));
        return Map.copyOf(merged);
    }

    /** 한 아이템이 두 파일에서 ALLY와 SELF로 갈리면 어느 쪽이 맞는지 알 수 없다. 조용히 덮어쓰지 않고 기동에서 멈춘다. */
    @SafeVarargs
    private static Map<Long, Synergy> mergeSynergies(Map<Long, ItemProfile>... sources) {
        Map<Long, Synergy> merged = new HashMap<>();
        for (Map<Long, ItemProfile> source : sources) {
            ItemProfile.synergyByItemId(source).forEach((itemId, synergy) -> {
                Synergy declared = merged.putIfAbsent(itemId, synergy);
                if (declared != null && declared != synergy) {
                    throw new IllegalStateException("아이템 synergy 선언이 충돌합니다: " + itemId);
                }
            });
        }
        return Map.copyOf(merged);
    }

    private final Map<Long, Set<ItemTrait>> traitsByItemId;
    private final Map<Long, Synergy> synergyByItemId;

    public ItemTraitCatalog() {
        this(DEFAULT_TRAITS, DEFAULT_SYNERGIES);
    }

    public ItemTraitCatalog(Map<Long, Set<ItemTrait>> traitsByItemId) {
        this(traitsByItemId, Map.of());
    }

    public ItemTraitCatalog(Map<Long, Set<ItemTrait>> traitsByItemId, Map<Long, Synergy> synergyByItemId) {
        Objects.requireNonNull(traitsByItemId, "아이템 trait 목록은 null일 수 없습니다.");
        Objects.requireNonNull(synergyByItemId, "아이템 synergy 목록은 null일 수 없습니다.");

        Map<Long, Set<ItemTrait>> copiedTraits = new HashMap<>();
        traitsByItemId.forEach((itemId, traits) -> {
            Objects.requireNonNull(itemId, "아이템 ID는 null일 수 없습니다.");
            Objects.requireNonNull(traits, "아이템 trait는 null일 수 없습니다.");
            copiedTraits.put(itemId, Set.copyOf(traits));
        });
        this.traitsByItemId = Map.copyOf(copiedTraits);
        this.synergyByItemId = Map.copyOf(synergyByItemId);
    }

    public Set<ItemTrait> traitsOf(Item item) {
        Objects.requireNonNull(item, "아이템은 null일 수 없습니다.");
        return traitsByItemId.getOrDefault(item.getItemId(), Set.of());
    }

    /** 선언하지 않은 아이템은 SELF다. 아군 이름을 잘못 붙이는 것보다 비워 두는 편이 낫다. */
    public Synergy synergyOf(Item item) {
        Objects.requireNonNull(item, "아이템은 null일 수 없습니다.");
        return synergyByItemId.getOrDefault(item.getItemId(), Synergy.SELF);
    }

    public boolean hasTrait(Item item, ItemTrait trait) {
        Objects.requireNonNull(trait, "아이템 trait는 null일 수 없습니다.");
        return traitsOf(item).contains(trait);
    }
}
