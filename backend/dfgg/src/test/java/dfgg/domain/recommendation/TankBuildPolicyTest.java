package dfgg.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TankBuildPolicyTest {

    private final Item armorItem = new Item(1L, "방어력 아이템", List.of("Armor"));
    private final Item spellBlockItem = new Item(2L, "마법 저항력 아이템", List.of("SpellBlock"));
    private final Item healthItem = new Item(3L, "체력 아이템", List.of("Health"));
    private final Item neutralItem = new Item(4L, "일반 아이템");
    private final Item secondArmorItem = new Item(5L, "방어력 아이템 2", List.of("Armor"));
    private final Item secondSpellBlockItem = new Item(6L, "마법 저항력 아이템 2", List.of("SpellBlock"));

    private final TankBuildPolicy policy = new TankBuildPolicy();

    @Test
    @DisplayName("Armor와 SpellBlock 태그로 TANK 빌드 방향을 분류한다")
    void evaluate_ClassifiesTankBuildDirections() {
        // given
        CoreBuildCluster physicalCluster = cluster(armorItem, healthItem, neutralItem);
        CoreBuildCluster magicCluster = cluster(spellBlockItem, healthItem, neutralItem);
        CoreBuildCluster mixedCluster = cluster(armorItem, spellBlockItem, healthItem);
        Champion enemy = champion(ChampionTag.FIGHTER, ChampionTag.MAGE);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(physicalCluster, magicCluster, mixedCluster),
                List.of(enemy)
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly("PHYSICAL_DAMAGE", "MAGIC_DAMAGE", "MIXED_DAMAGE");
        assertThat(candidates)
                .allSatisfy(candidate -> assertThat(candidate.direction().championTag())
                        .isEqualTo(ChampionTag.TANK));
    }

    @Test
    @DisplayName("Health만 있는 군집은 혼합 피해 대응으로 분류하지 않는다")
    void evaluate_WhenClusterHasOnlyHealth_ExcludesCluster() {
        // given
        CoreBuildCluster healthOnlyCluster = cluster(healthItem, neutralItem, new Item(7L, "일반 아이템 2"));

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(healthOnlyCluster),
                List.of(champion(ChampionTag.FIGHTER))
        );

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("Armor 태그가 더 많으면 물리 피해 대응으로 분류한다")
    void evaluate_WhenArmorTagsAreMore_ClassifiesPhysicalDirection() {
        // given
        CoreBuildCluster armorHeavyCluster = cluster(
                armorItem,
                secondArmorItem,
                spellBlockItem
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(armorHeavyCluster),
                List.of(champion(ChampionTag.FIGHTER))
        );

        // then
        assertThat(candidates)
                .singleElement()
                .extracting(candidate -> candidate.direction().code())
                .isEqualTo("PHYSICAL_DAMAGE");
    }

    @Test
    @DisplayName("SpellBlock 태그가 더 많으면 마법 피해 대응으로 분류한다")
    void evaluate_WhenSpellBlockTagsAreMore_ClassifiesMagicDirection() {
        // given
        CoreBuildCluster spellBlockHeavyCluster = cluster(
                spellBlockItem,
                secondSpellBlockItem,
                armorItem
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(spellBlockHeavyCluster),
                List.of(champion(ChampionTag.MAGE))
        );

        // then
        assertThat(candidates)
                .singleElement()
                .extracting(candidate -> candidate.direction().code())
                .isEqualTo("MAGIC_DAMAGE");
    }

    @Test
    @DisplayName("복수 태그를 가진 적 챔피언의 물리·마법 위협을 모두 반영한다")
    void evaluate_WhenEnemyHasMultipleTags_CountsEveryRelevantTag() {
        // given
        CoreBuildCluster physicalCluster = cluster(armorItem, healthItem, neutralItem);
        CoreBuildCluster magicCluster = cluster(spellBlockItem, healthItem, neutralItem);
        Champion hybridEnemy = champion(ChampionTag.ASSASSIN, ChampionTag.MAGE);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(physicalCluster, magicCluster),
                List.of(hybridEnemy)
        );

        // then
        assertThat(candidates)
                .extracting(BuildCandidate::suitabilityScore)
                .containsExactly(3.0, 3.0);
    }

    @Test
    @DisplayName("물리 위협이 더 많으면 물리 피해 대응 점수가 더 높다")
    void evaluate_WhenPhysicalThreatIsHigher_AssignsHigherPhysicalScore() {
        // given
        CoreBuildCluster physicalCluster = cluster(armorItem, healthItem, neutralItem);
        CoreBuildCluster magicCluster = cluster(spellBlockItem, healthItem, neutralItem);
        List<Champion> enemies = List.of(
                champion(ChampionTag.FIGHTER),
                champion(ChampionTag.MARKSMAN),
                champion(ChampionTag.MAGE)
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(physicalCluster, magicCluster),
                enemies
        );

        // then
        assertThat(candidates.get(0).suitabilityScore())
                .isGreaterThan(candidates.get(1).suitabilityScore());
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
