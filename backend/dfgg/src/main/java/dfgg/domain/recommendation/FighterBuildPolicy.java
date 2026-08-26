package dfgg.domain.recommendation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * FIGHTER 챔피언의 첫 3코어 군집을 대표 방향으로 분류하고,
 * 적 챔피언 조합에 대한 적합도를 계산한다.
 */
public final class FighterBuildPolicy implements ChampionBuildPolicy {

    private static final String ANTI_TANK = "ANTI_TANK";
    private static final String BURST_SURVIVAL = "BURST_SURVIVAL";
    private static final String SUSTAINED_COMBAT = "SUSTAINED_COMBAT";

    private static final Set<String> ANTI_TANK_ITEM_TAGS = Set.of(
            "ArmorPenetration",
            "MagicPenetration",
            "OnHit",
            "AttackSpeed"
    );

    private static final Set<String> BURST_SURVIVAL_ITEM_TAGS = Set.of(
            "Health",
            "Armor",
            "SpellBlock",
            "MagicResist",
            "Tenacity"
    );

    private static final Set<String> SUSTAINED_COMBAT_ITEM_TAGS = Set.of(
            "LifeSteal",
            "SpellVamp",
            "AbilityHaste",
            "HealthRegen"
    );

    private static final Set<ChampionTag> ANTI_TANK_ENEMY_TAGS = Set.of(
            ChampionTag.TANK
    );

    private static final Set<ChampionTag> BURST_ENEMY_TAGS = Set.of(
            ChampionTag.ASSASSIN
    );

    private static final Set<ChampionTag> SUSTAINED_COMBAT_ENEMY_TAGS = Set.of(
            ChampionTag.FIGHTER,
            ChampionTag.TANK
    );

    /**
     * 실제 관측된 빌드 군집을 FIGHTER 빌드 후보로 변환한다.
     *
     * <p>대표 방향을 하나로 결정할 수 없는 군집은 후보에 포함하지 않는다.
     */
    @Override
    public ChampionTag supportedTag() {
        return ChampionTag.FIGHTER;
    }

    @Override
    public List<BuildDirection> supportedDirections() {
        return List.of(
                new BuildDirection(ChampionTag.FIGHTER, ANTI_TANK),
                new BuildDirection(ChampionTag.FIGHTER, BURST_SURVIVAL),
                new BuildDirection(ChampionTag.FIGHTER, SUSTAINED_COMBAT)
        );
    }

    @Override
    public List<BuildCandidate> evaluate(
            List<CoreBuildCluster> clusters,
            List<Champion> enemies
    ) {
        Objects.requireNonNull(clusters, "빌드 군집 목록은 null일 수 없습니다.");
        Objects.requireNonNull(enemies, "적 챔피언 목록은 null일 수 없습니다.");

        int antiTankThreat = countThreat(enemies, ANTI_TANK_ENEMY_TAGS);
        int burstThreat = countThreat(enemies, BURST_ENEMY_TAGS);
        int sustainedCombatThreat = countThreat(enemies, SUSTAINED_COMBAT_ENEMY_TAGS);

        return clusters.stream()
                .map(cluster -> toCandidate(
                        cluster,
                        antiTankThreat,
                        burstThreat,
                        sustainedCombatThreat
                ))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<BuildCandidate> toCandidate(
            CoreBuildCluster cluster,
            int antiTankThreat,
            int burstThreat,
            int sustainedCombatThreat
    ) {
        Objects.requireNonNull(cluster, "빌드 군집은 null일 수 없습니다.");

        List<Item> coreItems = cluster.getRepresentativeSequence().getOrderedItems();
        int antiTankScore = countItemTagScore(coreItems, ANTI_TANK_ITEM_TAGS);
        int burstSurvivalScore = countItemTagScore(coreItems, BURST_SURVIVAL_ITEM_TAGS);
        int sustainedCombatScore = countItemTagScore(coreItems, SUSTAINED_COMBAT_ITEM_TAGS);

        Optional<String> directionCode = selectRepresentativeDirection(
                antiTankScore,
                burstSurvivalScore,
                sustainedCombatScore
        );

        if (directionCode.isEmpty()) {
            return Optional.empty();
        }

        double suitabilityScore = calculateSuitabilityScore(
                directionCode.get(),
                antiTankScore,
                burstSurvivalScore,
                sustainedCombatScore,
                antiTankThreat,
                burstThreat,
                sustainedCombatThreat
        );

        return Optional.of(new BuildCandidate(
                new BuildDirection(ChampionTag.FIGHTER, directionCode.get()),
                cluster,
                suitabilityScore
        ));
    }

    private Optional<String> selectRepresentativeDirection(
            int antiTankScore,
            int burstSurvivalScore,
            int sustainedCombatScore
    ) {
        int highestScore = Math.max(
                antiTankScore,
                Math.max(burstSurvivalScore, sustainedCombatScore)
        );

        if (highestScore == 0) {
            return Optional.empty();
        }

        int highestDirectionCount = 0;
        highestDirectionCount += antiTankScore == highestScore ? 1 : 0;
        highestDirectionCount += burstSurvivalScore == highestScore ? 1 : 0;
        highestDirectionCount += sustainedCombatScore == highestScore ? 1 : 0;

        // 최고점 방향이 여러 개면 임의의 대표 방향을 선택하지 않는다.
        if (highestDirectionCount > 1) {
            return Optional.empty();
        }

        if (antiTankScore == highestScore) {
            return Optional.of(ANTI_TANK);
        }
        if (burstSurvivalScore == highestScore) {
            return Optional.of(BURST_SURVIVAL);
        }
        return Optional.of(SUSTAINED_COMBAT);
    }

    private double calculateSuitabilityScore(
            String directionCode,
            int antiTankScore,
            int burstSurvivalScore,
            int sustainedCombatScore,
            int antiTankThreat,
            int burstThreat,
            int sustainedCombatThreat
    ) {
        // 초기 기준은 관련 아이템 태그와 적 챔피언 태그에 동일한 가중치를 적용한다.
        return switch (directionCode) {
            case ANTI_TANK -> antiTankScore * (antiTankThreat + 1);
            case BURST_SURVIVAL -> burstSurvivalScore * (burstThreat + 1);
            case SUSTAINED_COMBAT -> sustainedCombatScore * (sustainedCombatThreat + 1);
            default -> throw new IllegalArgumentException("알 수 없는 FIGHTER 빌드 방향입니다.");
        };
    }

    private int countItemTagScore(List<Item> items, Set<String> directionTags) {
        return (int) items.stream()
                .flatMap(item -> item.getTags().stream())
                .filter(directionTags::contains)
                .count();
    }

    private int countThreat(List<Champion> enemies, Set<ChampionTag> threatTags) {
        // 대표 태그 하나만 고르지 않고 각 적 챔피언이 가진 모든 태그를 확인한다.
        return (int) enemies.stream()
                .flatMap(enemy -> enemy.getChampionTags().stream())
                .filter(threatTags::contains)
                .count();
    }
}
