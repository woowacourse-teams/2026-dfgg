package dfgg.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MageBuildPolicyTest {

    private final Item neutralMageItem = new Item(1L, "일반 주문력 아이템", List.of("SpellDamage"));
    private final Item neutralItemA = new Item(2L, "일반 아이템 A");
    private final Item neutralItemB = new Item(3L, "일반 아이템 B");

    private final MageBuildPolicy policy = new MageBuildPolicy();

    @Test
    @DisplayName("정규화 점수가 가장 높은 MAGE 방향으로 분류한다")
    void evaluate_ClassifiesMageBuildDirectionsByNormalizedScore() {
        // given
        Item burstItem = new Item(
                4L,
                "순간 화력 아이템",
                List.of("SpellDamage", "MagicPenetration")
        );
        Item sustainedItem = new Item(
                5L,
                "지속 화력 아이템",
                List.of("SpellDamage", "AbilityHaste", "Mana", "SpellVamp")
        );
        Item survivalItem = new Item(
                6L,
                "생존 대응 아이템",
                List.of(
                        "SpellDamage",
                        "Health",
                        "Armor",
                        "SpellBlock",
                        "Tenacity",
                        "NonbootsMovement"
                )
        );
        CoreBuildCluster burstCluster = cluster(burstItem, neutralItemA, neutralItemB);
        CoreBuildCluster sustainedCluster = cluster(sustainedItem, neutralItemA, neutralItemB);
        CoreBuildCluster survivalCluster = cluster(survivalItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(burstCluster, sustainedCluster, survivalCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly("BURST_DAMAGE", "SUSTAINED_DAMAGE", "SURVIVAL_RESPONSE");
        assertThat(candidates)
                .allSatisfy(candidate -> assertThat(candidate.direction().championTag())
                        .isEqualTo(ChampionTag.MAGE));
    }

    @Test
    @DisplayName("방향별 최대 점수로 정규화하여 서로 다른 태그 기준 수를 보정한다")
    void evaluate_NormalizesScoresByDirectionCriteriaCount() {
        // given
        Item ludens = new Item(
                7L,
                "루덴의 메아리",
                List.of("SpellDamage", "AbilityHaste", "CooldownReduction", "Mana")
        );
        Item shadowflame = new Item(
                8L,
                "그림자불꽃",
                List.of("SpellDamage", "MagicPenetration")
        );
        Item rabadonsDeathcap = new Item(9L, "라바돈의 죽음모자", List.of("SpellDamage"));
        CoreBuildCluster cluster = cluster(ludens, shadowflame, rabadonsDeathcap);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.direction().code()).isEqualTo("BURST_DAMAGE");
                    assertThat(candidate.suitabilityScore()).isCloseTo(1.0 / 3.0, within(1.0e-10));
                });
    }

    @Test
    @DisplayName("SpellDamage가 없는 군집은 MAGE 빌드 후보에서 제외한다")
    void evaluate_WhenSpellDamageTagIsMissing_ExcludesCluster() {
        // given
        Item penetrationItem = new Item(10L, "관통 아이템", List.of("MagicPenetration"));
        CoreBuildCluster cluster = cluster(penetrationItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("SpellDamage와 MagicPenetration이 같은 아이템에 있을 때만 순간 화력 점수를 준다")
    void evaluate_WhenBurstTagsBelongToDifferentItems_DoesNotCombineThem() {
        // given
        Item spellDamageItem = new Item(11L, "주문력 아이템", List.of("SpellDamage"));
        Item penetrationItem = new Item(12L, "관통 아이템", List.of("MagicPenetration"));
        CoreBuildCluster cluster = cluster(spellDamageItem, penetrationItem, neutralItemA);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("같은 의미의 지속 화력 태그는 아이템 하나당 한 번만 반영한다")
    void evaluate_DeduplicatesCooldownTags() {
        // given
        Item item = new Item(
                13L,
                "재사용 대기시간 아이템",
                List.of("SpellDamage", "AbilityHaste", "CooldownReduction", "Mana", "SpellVamp")
        );
        CoreBuildCluster cluster = cluster(item, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.direction().code()).isEqualTo("SUSTAINED_DAMAGE");
                    assertThat(candidate.suitabilityScore()).isCloseTo(1.0 / 3.0, within(1.0e-10));
                });
    }

    @Test
    @DisplayName("같은 의미의 마법 저항력 태그는 아이템 하나당 한 번만 반영한다")
    void evaluate_DeduplicatesMagicResistanceTags() {
        // given
        Item item = new Item(
                14L,
                "마법 저항력 아이템",
                List.of("SpellDamage", "SpellBlock", "MagicResist")
        );
        CoreBuildCluster cluster = cluster(item, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.direction().code()).isEqualTo("SURVIVAL_RESPONSE");
                    assertThat(candidate.suitabilityScore()).isCloseTo(1.0 / 15.0, within(1.0e-10));
                });
    }

    @Test
    @DisplayName("정규화 최고점이 같은 군집은 임의의 대표 방향을 선택하지 않는다")
    void evaluate_WhenNormalizedScoresAreTied_ExcludesCluster() {
        // given
        Item burstItem = new Item(
                15L,
                "순간 화력 아이템",
                List.of("SpellDamage", "MagicPenetration")
        );
        Item cooldownAndManaItem = new Item(
                16L,
                "스킬 순환 아이템",
                List.of("AbilityHaste", "Mana")
        );
        Item spellVampItem = new Item(17L, "주문 흡혈 아이템", List.of("SpellVamp"));
        CoreBuildCluster cluster = cluster(burstItem, cooldownAndManaItem, spellVampItem);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("방향 태그 점수가 모두 0이면 내부 미분류 처리한다")
    void evaluate_WhenDirectionScoresAreZero_ExcludesCluster() {
        // given
        CoreBuildCluster cluster = cluster(neutralMageItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("복수 태그를 가진 적 챔피언을 각 MAGE 방향의 적합도에 반영한다")
    void evaluate_WhenEnemyHasMultipleTags_CountsEveryRelevantTag() {
        // given
        Item burstItem = new Item(
                18L,
                "순간 화력 아이템",
                List.of("SpellDamage", "MagicPenetration")
        );
        Item sustainedItem = new Item(
                19L,
                "지속 화력 아이템",
                List.of("SpellDamage", "AbilityHaste", "Mana", "SpellVamp")
        );
        Item survivalItem = new Item(
                20L,
                "생존 대응 아이템",
                List.of(
                        "SpellDamage",
                        "Health",
                        "Armor",
                        "SpellBlock",
                        "Tenacity",
                        "NonbootsMovement"
                )
        );
        CoreBuildCluster burstCluster = cluster(burstItem, neutralItemA, neutralItemB);
        CoreBuildCluster sustainedCluster = cluster(sustainedItem, neutralItemA, neutralItemB);
        CoreBuildCluster survivalCluster = cluster(survivalItem, neutralItemA, neutralItemB);
        Champion enemy = champion(
                ChampionTag.MAGE,
                ChampionTag.MARKSMAN,
                ChampionTag.TANK,
                ChampionTag.FIGHTER,
                ChampionTag.ASSASSIN
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(burstCluster, sustainedCluster, survivalCluster),
                List.of(enemy)
        );

        // then
        assertThat(candidates)
                .extracting(BuildCandidate::suitabilityScore)
                .containsExactly(1.0, 1.0, 2.0 / 3.0);
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
