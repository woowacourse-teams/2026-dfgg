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

class AssassinBuildPolicyTest {

    private final Item neutralItemA = new Item(1L, "일반 아이템 A");
    private final Item neutralItemB = new Item(2L, "일반 아이템 B");

    private final AssassinBuildPolicy policy = new AssassinBuildPolicy();

    @Test
    @DisplayName("첫 3코어의 태그 점수가 가장 높은 ASSASSIN 방향으로 분류한다")
    void evaluate_ClassifiesAssassinBuildDirections() {
        // given
        Item burstItem = new Item(
                3L,
                "순간 암살 아이템",
                List.of("Damage", "ArmorPenetration", "NonbootsMovement")
        );
        Item defenseNeutralizationItem = new Item(
                4L,
                "방어 무력화 아이템",
                List.of("ArmorPenetration", "OnHit", "AttackSpeed", "AbilityHaste")
        );
        Item engageSurvivalItem = new Item(
                5L,
                "진입 생존 아이템",
                List.of("Health", "Armor", "SpellBlock", "Tenacity", "LifeSteal", "SpellVamp")
        );
        CoreBuildCluster burstCluster = cluster(burstItem, neutralItemA, neutralItemB);
        CoreBuildCluster defenseCluster = cluster(
                defenseNeutralizationItem,
                neutralItemA,
                neutralItemB
        );
        CoreBuildCluster survivalCluster = cluster(engageSurvivalItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(burstCluster, defenseCluster, survivalCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly(
                        "BURST_ASSASSINATION",
                        "DEFENSE_NEUTRALIZATION",
                        "ENGAGE_SURVIVAL"
                );
        assertThat(candidates)
                .allSatisfy(candidate -> assertThat(candidate.direction().championTag())
                        .isEqualTo(ChampionTag.ASSASSIN));
    }

    @Test
    @DisplayName("관통 태그와 함께 관측된 태그 조합으로 대표 방향을 결정한다")
    void evaluate_UsesTagsObservedWithPenetration() {
        // given
        Item penetrationItem = new Item(6L, "관통 아이템", List.of("ArmorPenetration"));
        Item movementItem = new Item(7L, "접근 아이템", List.of("Damage", "NonbootsMovement"));
        Item onHitItem = new Item(8L, "적중 아이템", List.of("OnHit", "AttackSpeed"));
        CoreBuildCluster burstCluster = cluster(penetrationItem, movementItem, neutralItemA);
        CoreBuildCluster defenseCluster = cluster(penetrationItem, onHitItem, neutralItemA);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(burstCluster, defenseCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly("BURST_ASSASSINATION", "DEFENSE_NEUTRALIZATION");
    }

    @Test
    @DisplayName("관통 태그만으로 최고점이 같으면 임의의 대표 방향을 선택하지 않는다")
    void evaluate_WhenPenetrationScoresAreTied_ExcludesCluster() {
        // given
        Item penetrationItem = new Item(9L, "관통 아이템", List.of("MagicPenetration"));
        CoreBuildCluster cluster = cluster(penetrationItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("SpellBlock과 MagicResist는 아이템 하나당 한 번만 반영한다")
    void evaluate_DeduplicatesMagicResistanceTags() {
        // given
        Item magicResistanceItem = new Item(
                10L,
                "마법 저항력 아이템",
                List.of("SpellBlock", "MagicResist")
        );
        CoreBuildCluster cluster = cluster(magicResistanceItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.direction().code()).isEqualTo("ENGAGE_SURVIVAL");
                    assertThat(candidate.suitabilityScore()).isEqualTo(1.0);
                });
    }

    @Test
    @DisplayName("세 방향에 해당하는 태그가 없으면 내부 미분류 처리한다")
    void evaluate_WhenDirectionTagsAreMissing_ExcludesCluster() {
        // given
        CoreBuildCluster cluster = cluster(
                neutralItemA,
                neutralItemB,
                new Item(11L, "일반 아이템 C")
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("복수 태그를 가진 적 챔피언을 각 ASSASSIN 방향의 적합도에 반영한다")
    void evaluate_WhenEnemyHasMultipleTags_CountsEveryRelevantTag() {
        // given
        Item burstItem = new Item(12L, "순간 암살 단일 태그", List.of("Damage"));
        Item defenseItem = new Item(13L, "방어 무력화 단일 태그", List.of("OnHit"));
        Item survivalItem = new Item(14L, "진입 생존 단일 태그", List.of("Health"));
        CoreBuildCluster burstCluster = cluster(burstItem, neutralItemA, neutralItemB);
        CoreBuildCluster defenseCluster = cluster(defenseItem, neutralItemA, neutralItemB);
        CoreBuildCluster survivalCluster = cluster(survivalItem, neutralItemA, neutralItemB);
        Champion enemy = champion(
                ChampionTag.MAGE,
                ChampionTag.MARKSMAN,
                ChampionTag.TANK,
                ChampionTag.FIGHTER,
                ChampionTag.SUPPORT
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(burstCluster, defenseCluster, survivalCluster),
                List.of(enemy)
        );

        // then
        assertThat(candidates)
                .extracting(BuildCandidate::suitabilityScore)
                .containsExactly(3.0, 3.0, 3.0);
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
