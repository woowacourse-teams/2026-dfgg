package dfgg.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @DisplayName("한 아이템이 여러 배타 그룹에 속할 수 있다 — 피의 노래는 주문검이면서 세계의 결실이다")
    void isExclusiveWith_WhenItemBelongsToMultipleGroups_IsExclusiveInEitherGroup() {
        // given: 피의 노래(BLOODSONG)는 SPELLBLADE 그룹과 WORLD_ENDER 그룹에 둘 다 속한다
        long bloodsong = 3877L;
        long trinityForce = 3078L;   // SPELLBLADE만
        long dreamMaker = 3870L;     // WORLD_ENDER만
        ItemExclusionGroups groups = new ItemExclusionGroups(Map.of(
                bloodsong, Set.of("SPELLBLADE", "WORLD_ENDER"),
                trinityForce, Set.of("SPELLBLADE"),
                dreamMaker, Set.of("WORLD_ENDER")
        ));
        Item a = item(bloodsong, "피의 노래", List.of());
        Item b = item(trinityForce, "삼위일체", List.of());
        Item c = item(dreamMaker, "꿈 생성기", List.of());

        // when & then: 피의 노래는 두 그룹 각각의 아이템과 모두 배타 관계다
        assertThat(groups.isExclusiveWith(a, b)).isTrue();
        assertThat(groups.isExclusiveWith(a, c)).isTrue();
        // 삼위일체와 꿈 생성기는 공유하는 그룹이 없다
        assertThat(groups.isExclusiveWith(b, c)).isFalse();
    }

    @Test
    @DisplayName("규칙 목록을 주입해 바꿀 수 있다 — 패치로 배타 관계가 바뀌면 코드가 아니라 목록을 고친다")
    void isExclusiveWith_WhenCustomCatalogInjected_UsesIt() {
        // given
        ItemExclusionGroups custom = new ItemExclusionGroups(Map.of(
                INFINITY_EDGE, Set.of("MYTHIC_CRIT"), 6672L, Set.of("MYTHIC_CRIT")
        ));
        Item infinityEdge = item(INFINITY_EDGE, "무한의 대검", List.of("Damage"));
        Item kraken = item(6672L, "크라켄 학살자", List.of("Damage"));

        // when & then
        assertThat(custom.isExclusiveWith(infinityEdge, kraken)).isTrue();
    }

    @Test
    @DisplayName("주문검 계열끼리는 배타다 — 삼위일체와 리치베인")
    void isExclusiveWith_WhenBothAreSpellblade_AreExclusive() {
        Item trinityForce = item(3078L, "삼위일체", List.of());
        Item lichBane = item(3100L, "리치베인", List.of());
        assertThat(exclusionGroups.isExclusiveWith(trinityForce, lichBane)).isTrue();
    }

    @Test
    @DisplayName("생명선 계열끼리는 배타다 — 불멸의 철갑궁과 스테락의 도전")
    void isExclusiveWith_WhenBothAreLifeline_AreExclusive() {
        Item shieldbow = item(6673L, "불멸의 철갑궁", List.of());
        Item steraks = item(3053L, "스테락의 도전", List.of());
        assertThat(exclusionGroups.isExclusiveWith(shieldbow, steraks)).isTrue();
    }

    @Test
    @DisplayName("티아멧 계열끼리는 배타다 — 굶주린 히드라와 발걸음 분쇄기")
    void isExclusiveWith_WhenBothAreTiamat_AreExclusive() {
        Item ravenousHydra = item(3074L, "굶주린 히드라", List.of());
        Item stridebreaker = item(6631L, "발걸음 분쇄기", List.of());
        assertThat(exclusionGroups.isExclusiveWith(ravenousHydra, stridebreaker)).isTrue();
    }

    @Test
    @DisplayName("무효화 계열끼리는 배타다 — 밴시의 장막과 밤의 끝자락")
    void isExclusiveWith_WhenBothAreAnnul_AreExclusive() {
        Item banshees = item(3102L, "밴시의 장막", List.of());
        Item edgeOfNight = item(3814L, "밤의 끝자락", List.of());
        assertThat(exclusionGroups.isExclusiveWith(banshees, edgeOfNight)).isTrue();
    }

    @Test
    @DisplayName("불사르기 계열끼리는 배타다 — 공허한 광휘와 태양불꽃 방패")
    void isExclusiveWith_WhenBothAreImmolate_AreExclusive() {
        Item hollowRadiance = item(6664L, "공허한 광휘", List.of());
        Item sunfire = item(3068L, "태양불꽃 방패", List.of());
        assertThat(exclusionGroups.isExclusiveWith(hollowRadiance, sunfire)).isTrue();
    }

    @Test
    @DisplayName("세계의 결실 계열끼리는 배타다 — 자자크의 세계가시와 꿈 생성기")
    void isExclusiveWith_WhenBothAreWorldEnder_AreExclusive() {
        Item zaznak = item(3871L, "자자크의 세계가시", List.of());
        Item dreamMaker = item(3870L, "꿈 생성기", List.of());
        assertThat(exclusionGroups.isExclusiveWith(zaznak, dreamMaker)).isTrue();
    }

    @Test
    @DisplayName("경계는 방어구 관통력·마법 관통력 두 그룹 모두와 배타다")
    void isExclusiveWith_WhenItemIsBoundary_IsExclusiveWithBothPenetrationGroups() {
        Item boundary = item(3302L, "경계", List.of());
        Item serylda = item(6694L, "세릴다의 원한", List.of());       // 방어구 관통력
        Item voidStaff = item(3135L, "공허의 지팡이", List.of());      // 마법 관통력

        assertThat(exclusionGroups.isExclusiveWith(boundary, serylda)).isTrue();
        assertThat(exclusionGroups.isExclusiveWith(boundary, voidStaff)).isTrue();
    }

    @Test
    @DisplayName("피의 노래는 주문검·세계의 결실 두 그룹 모두와 배타다")
    void isExclusiveWith_WhenItemIsBloodsong_IsExclusiveWithBothGroups() {
        Item bloodsong = item(3877L, "피의 노래", List.of());
        Item trinityForce = item(3078L, "삼위일체", List.of());     // 주문검
        Item celestialOpp = item(3869L, "천상의 이의", List.of());   // 세계의 결실

        assertThat(exclusionGroups.isExclusiveWith(bloodsong, trinityForce)).isTrue();
        assertThat(exclusionGroups.isExclusiveWith(bloodsong, celestialOpp)).isTrue();
    }

    @Test
    @DisplayName("서로 다른 계열끼리는 배타가 아니다 — 방어구 관통력과 생명선")
    void isExclusiveWith_WhenDifferentCuratedGroups_AreNotExclusive() {
        Item blackCleaverItem = item(3071L, "칠흑의 양날 도끼", List.of());
        Item shieldbow = item(6673L, "불멸의 철갑궁", List.of());
        assertThat(exclusionGroups.isExclusiveWith(blackCleaverItem, shieldbow)).isFalse();
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
