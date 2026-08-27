package dfgg.domain.recommendation;

import static dfgg.domain.item.ItemTrait.ENGAGE;
import static dfgg.domain.item.ItemTrait.HEAL;
import static dfgg.domain.item.ItemTrait.PEEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemTraitCatalog;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SupportBuildPolicyTest {

    private final Item neutralItemA = new Item(1L, "일반 아이템 A");
    private final Item neutralItemB = new Item(2L, "일반 아이템 B");

    private final SupportBuildPolicy policy = new SupportBuildPolicy();

    @Test
    @DisplayName("Data Dragon 태그 점수가 가장 높은 SUPPORT 방향으로 분류한다")
    void evaluate_ClassifiesSupportBuildDirectionsByItemTags() {
        // given
        Item engageItem = new Item(
                100L,
                "진입 아이템",
                List.of("Health", "Tenacity", "NonbootsMovement", "Active")
        );
        Item protectionItem = new Item(
                101L,
                "아군 보호 아이템",
                List.of("AbilityHaste", "Slow", "Aura", "Armor")
        );
        Item enhancementItem = new Item(
                102L,
                "회복 강화 아이템",
                List.of("ManaRegen", "HealthRegen", "AbilityHaste", "SpellDamage")
        );
        CoreBuildCluster engageCluster = cluster(engageItem, neutralItemA, neutralItemB);
        CoreBuildCluster protectionCluster = cluster(protectionItem, neutralItemA, neutralItemB);
        CoreBuildCluster enhancementCluster = cluster(enhancementItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(engageCluster, protectionCluster, enhancementCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly("ENGAGE_INITIATION", "ALLY_PROTECTION", "HEALING_ENHANCEMENT");
        assertThat(candidates)
                .allSatisfy(candidate -> assertThat(candidate.direction().championTag())
                        .isEqualTo(ChampionTag.SUPPORT));
    }

    @Test
    @DisplayName("수동 trait를 Data Dragon 태그와 함께 방향 점수에 반영한다")
    void evaluate_UsesManualItemTraits() {
        // given
        Item engageItem = new Item(200L, "ENGAGE 아이템");
        Item peelItem = new Item(201L, "PEEL 아이템");
        Item healItem = new Item(202L, "HEAL 아이템");
        ItemTraitCatalog catalog = new ItemTraitCatalog(Map.of(
                200L, Set.of(ENGAGE),
                201L, Set.of(PEEL),
                202L, Set.of(HEAL)
        ));
        SupportBuildPolicy traitPolicy = new SupportBuildPolicy(catalog);
        CoreBuildCluster engageCluster = cluster(engageItem, neutralItemA, neutralItemB);
        CoreBuildCluster peelCluster = cluster(peelItem, neutralItemA, neutralItemB);
        CoreBuildCluster healCluster = cluster(healItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = traitPolicy.evaluate(
                List.of(engageCluster, peelCluster, healCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly("ENGAGE_INITIATION", "ALLY_PROTECTION", "HEALING_ENHANCEMENT");
        assertThat(candidates)
                .extracting(BuildCandidate::suitabilityScore)
                .containsExactly(2.0 / 27.0, 2.0 / 27.0, 2.0 / 33.0);
    }

    @Test
    @DisplayName("AbilityHaste와 CooldownReduction은 아이템 하나당 한 번만 반영한다")
    void evaluate_DeduplicatesCooldownTags() {
        // given
        Item item = new Item(
                300L,
                "스킬 가속 아이템",
                List.of("AbilityHaste", "CooldownReduction", "ManaRegen", "SpellDamage")
        );
        CoreBuildCluster cluster = cluster(item, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.direction().code()).isEqualTo("HEALING_ENHANCEMENT");
                    assertThat(candidate.suitabilityScore())
                            .isCloseTo(1.0 / 11.0, within(1.0e-10));
                });
    }

    @Test
    @DisplayName("SpellBlock과 MagicResist는 아이템 하나당 한 번만 반영한다")
    void evaluate_DeduplicatesMagicResistanceTags() {
        // given
        Item magicResistanceItem = new Item(
                301L,
                "마법 저항력 아이템",
                List.of("SpellBlock", "MagicResist")
        );
        Item engageTraitItem = new Item(302L, "진입 trait 아이템");
        ItemTraitCatalog catalog = new ItemTraitCatalog(Map.of(302L, Set.of(ENGAGE)));
        SupportBuildPolicy traitPolicy = new SupportBuildPolicy(catalog);
        CoreBuildCluster cluster = cluster(
                magicResistanceItem,
                engageTraitItem,
                neutralItemA
        );

        // when
        List<BuildCandidate> candidates = traitPolicy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.direction().code()).isEqualTo("ENGAGE_INITIATION");
                    assertThat(candidate.suitabilityScore())
                            .isCloseTo(1.0 / 9.0, within(1.0e-10));
                });
    }

    @Test
    @DisplayName("최고점 방향이 여러 개면 임의의 대표 방향을 선택하지 않는다")
    void evaluate_WhenHighestDirectionIsTied_ExcludesCluster() {
        // given
        Item sharedItem = new Item(400L, "공통 아이템", List.of("Health"));
        CoreBuildCluster cluster = cluster(sharedItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("관련 태그와 trait가 모두 없으면 내부 미분류 처리한다")
    void evaluate_WhenDirectionSignalsAreMissing_ExcludesCluster() {
        // given
        CoreBuildCluster cluster = cluster(
                neutralItemA,
                neutralItemB,
                new Item(401L, "일반 아이템 C")
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("복수 태그를 가진 적 챔피언을 각 SUPPORT 방향의 적합도에 반영한다")
    void evaluate_WhenEnemyHasMultipleTags_CountsEveryRelevantTag() {
        // given
        Item engageItem = new Item(500L, "진입 단일 태그", List.of("NonbootsMovement"));
        Item peelItem = new Item(501L, "PEEL trait 아이템");
        Item enhancementItem = new Item(502L, "회복 강화 단일 태그", List.of("ManaRegen"));
        ItemTraitCatalog catalog = new ItemTraitCatalog(Map.of(501L, Set.of(PEEL)));
        SupportBuildPolicy traitPolicy = new SupportBuildPolicy(catalog);
        CoreBuildCluster engageCluster = cluster(engageItem, neutralItemA, neutralItemB);
        CoreBuildCluster peelCluster = cluster(peelItem, neutralItemA, neutralItemB);
        CoreBuildCluster enhancementCluster = cluster(enhancementItem, neutralItemA, neutralItemB);
        Champion enemy = champion(
                ChampionTag.MAGE,
                ChampionTag.MARKSMAN,
                ChampionTag.ASSASSIN,
                ChampionTag.FIGHTER,
                ChampionTag.TANK
        );

        // when
        List<BuildCandidate> candidates = traitPolicy.evaluate(
                List.of(engageCluster, peelCluster, enhancementCluster),
                List.of(enemy)
        );

        // then
        assertThat(candidates)
                .extracting(BuildCandidate::suitabilityScore)
                .containsExactly(1.0 / 9.0, 2.0 / 9.0, 4.0 / 33.0);
    }

    private CoreBuildCluster cluster(Item... items) {
        ChampionBuildStats stats = mock(ChampionBuildStats.class);
        given(stats.getItems()).willReturn(List.of(items));

        List<Long> clusterKey = List.of(items).stream()
                .map(Item::getItemId)
                .sorted()
                .toList();

        return CoreBuildCluster.from(clusterKey, List.of(stats));
    }

    private Champion champion(ChampionTag... tags) {
        return new Champion(
                (long) tags.hashCode(),
                "enemy-" + tags.hashCode(),
                "적 챔피언",
                List.of(tags)
        );
    }
}
