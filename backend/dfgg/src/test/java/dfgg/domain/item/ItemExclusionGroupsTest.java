package dfgg.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemExclusionGroupsTest {

    private static final long BLACK_CLEAVER = 3071L;
    private static final long SERYLDAS_GRUDGE = 6694L;
    private static final long BERSERKERS_GREAVES = 3006L;
    private static final long PLATED_STEELCAPS = 3047L;
    private static final long INFINITY_EDGE = 3031L;

    private final ItemExclusionGroups exclusionGroups = new ItemExclusionGroups();

    private Item item(long itemId, String name, List<String> tags) {
        return new Item(itemId, name, tags);
    }

    @Test
    @DisplayName("신발끼리는 같은 배타 그룹이다 — 태그에서 유도하므로 새 신발이 나와도 자동으로 적용된다")
    void isExclusiveWith_WhenBothAreBoots_AreExclusive() {
        // given
        Item berserkers = item(BERSERKERS_GREAVES, "광전사의 군화", List.of("Boots"));
        Item plated = item(PLATED_STEELCAPS, "판금 장화", List.of("Boots"));

        // when & then
        assertThat(exclusionGroups.isExclusiveWith(berserkers, plated)).isTrue();
    }

    @Test
    @DisplayName("큐레이션된 상호배타 쌍은 같은 그룹이다 — 칠흑의 양날도끼와 셰릴다의 원한")
    void isExclusiveWith_WhenCuratedPair_AreExclusive() {
        // given: 태그만으로는 알 수 없어 손으로 관리하는 규칙이다
        Item blackCleaver = item(BLACK_CLEAVER, "칠흑의 양날도끼", List.of("Damage", "ArmorPenetration"));
        Item seryldas = item(SERYLDAS_GRUDGE, "셰릴다의 원한", List.of("Damage", "ArmorPenetration"));

        // when & then
        assertThat(exclusionGroups.isExclusiveWith(blackCleaver, seryldas)).isTrue();
    }

    @Test
    @DisplayName("배타 관계는 방향이 없다")
    void isExclusiveWith_WhenArgumentsSwapped_GivesSameAnswer() {
        // given
        Item blackCleaver = item(BLACK_CLEAVER, "칠흑의 양날도끼", List.of("Damage"));
        Item seryldas = item(SERYLDAS_GRUDGE, "셰릴다의 원한", List.of("Damage"));

        // when & then
        assertThat(exclusionGroups.isExclusiveWith(seryldas, blackCleaver))
                .isEqualTo(exclusionGroups.isExclusiveWith(blackCleaver, seryldas));
    }

    @Test
    @DisplayName("그룹이 없는 아이템끼리는 배타가 아니다")
    void isExclusiveWith_WhenNeitherBelongsToAGroup_AreNotExclusive() {
        // given
        Item infinityEdge = item(INFINITY_EDGE, "무한의 대검", List.of("Damage", "CriticalStrike"));
        Item other = item(6672L, "크라켄 학살자", List.of("Damage", "AttackSpeed"));

        // when & then
        assertThat(exclusionGroups.isExclusiveWith(infinityEdge, other)).isFalse();
    }

    @Test
    @DisplayName("신발과 일반 아이템은 배타가 아니다")
    void isExclusiveWith_WhenGroupsDiffer_AreNotExclusive() {
        // given
        Item berserkers = item(BERSERKERS_GREAVES, "광전사의 군화", List.of("Boots"));
        Item blackCleaver = item(BLACK_CLEAVER, "칠흑의 양날도끼", List.of("Damage"));

        // when & then
        assertThat(exclusionGroups.isExclusiveWith(berserkers, blackCleaver)).isFalse();
    }

    @Test
    @DisplayName("규칙 목록을 주입해 바꿀 수 있다 — 패치로 배타 관계가 바뀌면 코드가 아니라 목록을 고친다")
    void isExclusiveWith_WhenCustomCatalogInjected_UsesIt() {
        // given
        ItemExclusionGroups custom = new ItemExclusionGroups(Map.of(
                INFINITY_EDGE, "MYTHIC_CRIT", 6672L, "MYTHIC_CRIT"
        ));
        Item infinityEdge = item(INFINITY_EDGE, "무한의 대검", List.of("Damage"));
        Item kraken = item(6672L, "크라켄 학살자", List.of("Damage"));

        // when & then
        assertThat(custom.isExclusiveWith(infinityEdge, kraken)).isTrue();
    }

    @Test
    @DisplayName("같은 아이템끼리는 배타로 보지 않는다 — 중복 보유는 '이미 산 아이템' 규칙이 처리한다")
    void isExclusiveWith_WhenSameItem_IsNotTreatedAsExclusive() {
        // given
        Item blackCleaver = item(BLACK_CLEAVER, "칠흑의 양날도끼", List.of("Damage"));

        // when & then
        assertThat(exclusionGroups.isExclusiveWith(blackCleaver, blackCleaver)).isFalse();
    }
}
