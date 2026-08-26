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

class FighterBuildPolicyTest {

    private final Item antiTankItem = new Item(
            1L,
            "대탱커 아이템",
            List.of("ArmorPenetration", "OnHit", "AttackSpeed")
    );
    private final Item burstSurvivalItem = new Item(
            2L,
            "폭발 피해 생존 아이템",
            List.of("Health", "Armor", "Tenacity")
    );
    private final Item sustainedCombatItem = new Item(
            3L,
            "지속 교전 아이템",
            List.of("LifeSteal", "AbilityHaste", "HealthRegen")
    );
    private final Item neutralItemA = new Item(4L, "일반 아이템 A");
    private final Item neutralItemB = new Item(5L, "일반 아이템 B");

    private final FighterBuildPolicy policy = new FighterBuildPolicy();

    @Test
    @DisplayName("첫 3코어의 태그 점수가 가장 높은 FIGHTER 방향으로 분류한다")
    void evaluate_ClassifiesFighterBuildDirections() {
        // given
        CoreBuildCluster antiTankCluster = cluster(antiTankItem, neutralItemA, neutralItemB);
        CoreBuildCluster burstSurvivalCluster = cluster(burstSurvivalItem, neutralItemA, neutralItemB);
        CoreBuildCluster sustainedCombatCluster = cluster(sustainedCombatItem, neutralItemA, neutralItemB);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(antiTankCluster, burstSurvivalCluster, sustainedCombatCluster),
                List.of()
        );

        // then
        assertThat(candidates)
                .extracting(candidate -> candidate.direction().code())
                .containsExactly("ANTI_TANK", "BURST_SURVIVAL", "SUSTAINED_COMBAT");
        assertThat(candidates)
                .allSatisfy(candidate -> assertThat(candidate.direction().championTag())
                        .isEqualTo(ChampionTag.FIGHTER));
    }

    @Test
    @DisplayName("아이템이 가진 모든 관련 태그를 방향 점수에 반영한다")
    void evaluate_CountsEveryRelevantItemTag() {
        // given
        Item hybridItem = new Item(6L, "복합 아이템", List.of("OnHit", "LifeSteal"));
        Item attackSpeedItem = new Item(7L, "공격 속도 아이템", List.of("AttackSpeed"));
        CoreBuildCluster cluster = cluster(hybridItem, attackSpeedItem, neutralItemA);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates)
                .singleElement()
                .extracting(candidate -> candidate.direction().code())
                .isEqualTo("ANTI_TANK");
    }

    @Test
    @DisplayName("최고점 방향이 여러 개면 임의의 대표 방향을 선택하지 않는다")
    void evaluate_WhenHighestDirectionIsTied_ExcludesCluster() {
        // given
        Item onHitItem = new Item(8L, "적중 아이템", List.of("OnHit"));
        Item lifeStealItem = new Item(9L, "흡혈 아이템", List.of("LifeSteal"));
        CoreBuildCluster tiedCluster = cluster(onHitItem, lifeStealItem, neutralItemA);

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(tiedCluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("세 방향에 해당하는 태그가 없으면 내부 미분류 처리한다")
    void evaluate_WhenDirectionTagsAreMissing_ExcludesCluster() {
        // given
        CoreBuildCluster unclassifiedCluster = cluster(
                neutralItemA,
                neutralItemB,
                new Item(10L, "일반 아이템 C")
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(unclassifiedCluster), List.of());

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("복수 태그를 가진 적 챔피언을 각 FIGHTER 방향의 적합도에 반영한다")
    void evaluate_WhenEnemyHasMultipleTags_CountsEveryRelevantTag() {
        // given
        Item antiTankSingleTag = new Item(11L, "대탱커 단일 태그", List.of("OnHit"));
        Item burstSingleTag = new Item(12L, "폭발 생존 단일 태그", List.of("Health"));
        Item sustainedSingleTag = new Item(13L, "지속 교전 단일 태그", List.of("LifeSteal"));
        CoreBuildCluster antiTankCluster = cluster(antiTankSingleTag, neutralItemA, neutralItemB);
        CoreBuildCluster burstCluster = cluster(burstSingleTag, neutralItemA, neutralItemB);
        CoreBuildCluster sustainedCluster = cluster(sustainedSingleTag, neutralItemA, neutralItemB);
        Champion enemy = champion(ChampionTag.TANK, ChampionTag.FIGHTER, ChampionTag.ASSASSIN);

        // when
        List<BuildCandidate> candidates = policy.evaluate(
                List.of(antiTankCluster, burstCluster, sustainedCluster),
                List.of(enemy)
        );

        // then
        assertThat(candidates)
                .extracting(BuildCandidate::suitabilityScore)
                .containsExactly(2.0, 2.0, 3.0);
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
