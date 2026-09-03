package dfgg.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChampionBuildPolicyScoreContractTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("policyCases")
    @DisplayName("조합 위협이 없을 때 모든 정책은 0과 1 사이의 적합도를 반환한다")
    void evaluate_WithoutCompositionThreat_ReturnsComparableScore(
            String policyName,
            ChampionBuildPolicy policy,
            Item directionItem
    ) {
        // given
        CoreBuildCluster cluster = cluster(
                directionItem,
                new Item(directionItem.getItemId() + 100L, "일반 아이템 A"),
                new Item(directionItem.getItemId() + 200L, "일반 아이템 B")
        );

        // when
        List<BuildCandidate> candidates = policy.evaluate(List.of(cluster), List.of());

        // then
        assertThat(candidates)
                .as(policyName)
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.suitabilityScore())
                        .isStrictlyBetween(0.0, 1.0));
    }

    private static Stream<Arguments> policyCases() {
        return Stream.of(
                Arguments.of(
                        "TANK",
                        new TankBuildPolicy(),
                        new Item(1L, "물리 방어", List.of("Armor"))
                ),
                Arguments.of(
                        "FIGHTER",
                        new FighterBuildPolicy(),
                        new Item(2L, "대탱커", List.of("OnHit"))
                ),
                Arguments.of(
                        "MAGE",
                        new MageBuildPolicy(),
                        new Item(
                                3L,
                                "순간 화력",
                                List.of("SpellDamage", "MagicPenetration")
                        )
                ),
                Arguments.of(
                        "ASSASSIN",
                        new AssassinBuildPolicy(),
                        new Item(4L, "순간 암살", List.of("Damage"))
                ),
                Arguments.of(
                        "MARKSMAN",
                        new MarksmanBuildPolicy(),
                        new Item(5L, "치명타", List.of("CriticalStrike"))
                ),
                Arguments.of(
                        "SUPPORT",
                        new SupportBuildPolicy(),
                        new Item(6L, "진입", List.of("NonbootsMovement"))
                )
        );
    }

    private static CoreBuildCluster cluster(Item... items) {
        ChampionBuildStats stats = mock(ChampionBuildStats.class);
        given(stats.getItems()).willReturn(List.of(items));

        List<Long> clusterKey = List.of(items).stream()
                .map(Item::getItemId)
                .sorted()
                .toList();

        return CoreBuildCluster.from(clusterKey, List.of(stats));
    }
}
