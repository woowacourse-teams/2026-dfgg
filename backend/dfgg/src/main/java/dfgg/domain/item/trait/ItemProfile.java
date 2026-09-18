package dfgg.domain.item.trait;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 역할 파일의 한 항목. synergy와 특성을 한 줄에 함께 선언한다.
 * <p>
 * 팩토리 이름이 곧 선택이라 기본값이 없다 — 특성을 채우는 사람이 대상도 같이 정한다.
 */
record ItemProfile(Synergy synergy, Set<ItemTrait> traits) {

    ItemProfile {
        Objects.requireNonNull(synergy, "아이템 synergy는 null일 수 없습니다.");
        Objects.requireNonNull(traits, "아이템 trait는 null일 수 없습니다.");
        traits = Set.copyOf(traits);
    }

    static ItemProfile ally(ItemTrait... traits) {
        return new ItemProfile(Synergy.ALLY, Set.of(traits));
    }

    static ItemProfile self(ItemTrait... traits) {
        return new ItemProfile(Synergy.SELF, Set.of(traits));
    }

    static Map<Long, Set<ItemTrait>> traitsByItemId(Map<Long, ItemProfile> profiles) {
        Map<Long, Set<ItemTrait>> traits = new HashMap<>();
        profiles.forEach((itemId, profile) -> traits.put(itemId, profile.traits()));
        return Map.copyOf(traits);
    }

    static Map<Long, Synergy> synergyByItemId(Map<Long, ItemProfile> profiles) {
        Map<Long, Synergy> synergies = new HashMap<>();
        profiles.forEach((itemId, profile) -> synergies.put(itemId, profile.synergy()));
        return Map.copyOf(synergies);
    }
}
