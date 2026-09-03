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

class MarksmanBuildPolicyTest {

    private final Item neutralItemA = new Item(1L, "일반 아이템 A");
    private final Item neutralItemB = new Item(2L, "일반 아이템 B");

    private final MarksmanBuildPolicy policy = new MarksmanBuildPolicy();

    @Test
    @DisplayName("첫 3코어의 태그 점수가 가장 높은 MARKSMAN 방향으로 분류한다")
    void evaluate_ClassifiesMarksmanBuildDirections() {
        // given
        Item criticalStrikeItem = new Item(
                3L,
                "치명타 화력 아이템",
                List.of("CriticalStrike", "Damage", "AttackSpeed")
        );
        Item antiTankItem = new Item(
                4L,
                "대탱커 지속딜 아이템",
                List.of("OnHit", "AttackSpeed", "ArmorPenetration", "MagicPenetration")
        );
        Item survivalKitingItem = new Item(
                5L,
                "생존 카이팅 아이템",
                List.of("LifeSteal", "NonbootsMovement", "Health", "Tenacity", "Slow")
        );
        CoreBuildCluster criticalStrikeCluster = cluster(criticalStrikeItem, neutralItemA, neutralItemB);
        CoreBuildCluster antiTankCluster = cluster(antiTankItem, neutralItemA, neutralItemB);
        CoreBuildCluster survivalKitingCluster = cluster(
                survivalKitingItem,
                neutralItemA,
                neutralItemB
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(criticalStrikeCluster, antiTankCluster, survivalKitingCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly(
                        "CRITICAL_STRIKE_DAMAGE",
                        "ANTI_TANK_SUSTAINED_DAMAGE",
                        "SURVIVAL_KITING"
                );
        assertThat(candidates)
                .allSatisfy(candidate -> assertThat(candidate.direction().championTag())
                        .isEqualTo(ChampionTag.MARKSMAN));
    }

    @Test
    @DisplayName("CriticalStrike와 OnHit으로 두 공격 방향을 구분한다")
    void evaluate_UsesCriticalStrikeAndOnHitAsPrimarySignals() {
        // given
        Item sharedAttackSpeedItem = new Item(6L, "공격 속도 아이템", List.of("AttackSpeed"));
        Item criticalStrikeItem = new Item(7L, "치명타 아이템", List.of("CriticalStrike"));
        Item onHitItem = new Item(8L, "적중 아이템", List.of("OnHit"));
        CoreBuildCluster criticalStrikeCluster = cluster(
                sharedAttackSpeedItem,
                criticalStrikeItem,
                neutralItemA
        );
        CoreBuildCluster antiTankCluster = cluster(sharedAttackSpeedItem, onHitItem, neutralItemA);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(criticalStrikeCluster, antiTankCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly("CRITICAL_STRIKE_DAMAGE", "ANTI_TANK_SUSTAINED_DAMAGE");
    }

    @Test
    @DisplayName("공통 공격 태그만으로 최고점이 같으면 임의의 대표 방향을 선택하지 않는다")
    void evaluate_WhenSharedAttackScoresAreTied_ExcludesCluster() {
        // given
        Item sharedItem = new Item(
                9L,
                "공통 공격 아이템",
                List.of("AttackSpeed", "ArmorPenetration")
        );
        CoreBuildCluster cluster = cluster(sharedItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("LifeSteal과 함께 관측된 태그로 대탱커와 생존 카이팅을 구분한다")
    void evaluate_UsesTagsObservedWithLifeSteal() {
        // given
        Item lifeStealItem = new Item(10L, "생명력 흡수 아이템", List.of("LifeSteal"));
        Item onHitItem = new Item(11L, "적중 아이템", List.of("OnHit", "AttackSpeed"));
        Item movementItem = new Item(
                12L,
                "카이팅 아이템",
                List.of("NonbootsMovement", "Slow")
        );
        CoreBuildCluster antiTankCluster = cluster(lifeStealItem, onHitItem, neutralItemA);
        CoreBuildCluster survivalCluster = cluster(lifeStealItem, movementItem, neutralItemA);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(antiTankCluster, survivalCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly("ANTI_TANK_SUSTAINED_DAMAGE", "SURVIVAL_KITING");
    }

    @Test
    @DisplayName("SpellBlock과 MagicResist는 아이템 하나당 한 번만 반영한다")
    void evaluate_DeduplicatesMagicResistanceTags() {
        // given
        Item magicResistanceItem = new Item(
                13L,
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
                    assertThat(candidate.direction().code()).isEqualTo("SURVIVAL_KITING");
                    assertThat(candidate.suitabilityScore())
                            .isCloseTo(1.0 / 21.0, within(1.0e-10));
                });
    }

    @Test
    @DisplayName("각 MARKSMAN 방향의 최대 아이템 점수를 1로 정규화한다")
    void evaluate_NormalizesMaximumItemScoreForEveryDirection() {
        // given
        Item criticalStrikeItem = new Item(
                18L,
                "치명타 최대 점수 아이템",
                List.of("CriticalStrike", "Damage", "AttackSpeed", "ArmorPenetration")
        );
        Item antiTankItem = new Item(
                19L,
                "대탱커 최대 점수 아이템",
                List.of(
                        "OnHit",
                        "AttackSpeed",
                        "ArmorPenetration",
                        "MagicPenetration",
                        "LifeSteal"
                )
        );
        Item survivalItem = new Item(
                20L,
                "생존 최대 점수 아이템",
                List.of(
                        "LifeSteal",
                        "NonbootsMovement",
                        "Health",
                        "Armor",
                        "SpellBlock",
                        "Tenacity",
                        "Slow"
                )
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(
                        cluster(criticalStrikeItem, criticalStrikeItem, criticalStrikeItem),
                        cluster(antiTankItem, antiTankItem, antiTankItem),
                        cluster(survivalItem, survivalItem, survivalItem)
                ),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(BuildCandidate::suitabilityScore)
                .containsExactly(1.0, 1.0, 1.0);
    }

    @Test
    @DisplayName("세 방향에 해당하는 태그가 없으면 내부 미분류 처리한다")
    void evaluate_WhenDirectionTagsAreMissing_ExcludesCluster() {
        // given
        CoreBuildCluster cluster = cluster(
                neutralItemA,
                neutralItemB,
                new Item(14L, "일반 아이템 C")
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("복수 태그를 가진 적 챔피언을 각 MARKSMAN 방향의 적합도에 반영한다")
    void evaluate_WhenEnemyHasMultipleTags_CountsEveryRelevantTag() {
        // given
        Item criticalStrikeItem = new Item(15L, "치명타 단일 태그", List.of("CriticalStrike"));
        Item antiTankItem = new Item(16L, "적중 단일 태그", List.of("OnHit"));
        Item survivalItem = new Item(17L, "이동 단일 태그", List.of("NonbootsMovement"));
        CoreBuildCluster criticalStrikeCluster = cluster(
                criticalStrikeItem,
                neutralItemA,
                neutralItemB
        );
        CoreBuildCluster antiTankCluster = cluster(antiTankItem, neutralItemA, neutralItemB);
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
                List.of(criticalStrikeCluster, antiTankCluster, survivalCluster),
                List.of(enemy)
        );

        // then
        assertThat(candidates)
                .extracting(BuildCandidate::suitabilityScore)
                .containsExactly(
                        1.0 / 4.0,
                        1.0 / 5.0,
                        1.0 / 7.0
                );
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
