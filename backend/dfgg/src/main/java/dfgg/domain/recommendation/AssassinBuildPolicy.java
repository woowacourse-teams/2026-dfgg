package dfgg.domain.recommendation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ASSASSIN 챔피언의 첫 3코어 군집을 대표 방향으로 분류하고,
 * 적 챔피언 조합에 대한 적합도를 계산한다.
 */
public final class AssassinBuildPolicy implements ChampionBuildPolicy {

    private static final String BURST_ASSASSINATION = "BURST_ASSASSINATION";
    private static final String DEFENSE_NEUTRALIZATION = "DEFENSE_NEUTRALIZATION";
    private static final String ENGAGE_SURVIVAL = "ENGAGE_SURVIVAL";

    private static final String DAMAGE_TAG = "Damage";
    private static final String SPELL_DAMAGE_TAG = "SpellDamage";
    private static final String ARMOR_PENETRATION_TAG = "ArmorPenetration";
    private static final String MAGIC_PENETRATION_TAG = "MagicPenetration";
    private static final String NONBOOTS_MOVEMENT_TAG = "NonbootsMovement";
    private static final String ON_HIT_TAG = "OnHit";
    private static final String ATTACK_SPEED_TAG = "AttackSpeed";
    private static final String ABILITY_HASTE_TAG = "AbilityHaste";
    private static final String HEALTH_TAG = "Health";
    private static final String ARMOR_TAG = "Armor";
    private static final String SPELL_BLOCK_TAG = "SpellBlock";
    private static final String MAGIC_RESIST_TAG = "MagicResist";
    private static final String TENACITY_TAG = "Tenacity";
    private static final String LIFE_STEAL_TAG = "LifeSteal";
    private static final String SPELL_VAMP_TAG = "SpellVamp";

    private static final Set<ChampionTag> BURST_ASSASSINATION_ENEMY_TAGS = Set.of(
            ChampionTag.MAGE,
            ChampionTag.MARKSMAN
    );

    private static final Set<ChampionTag> DEFENSE_NEUTRALIZATION_ENEMY_TAGS = Set.of(
            ChampionTag.TANK,
            ChampionTag.FIGHTER
    );

    private static final Set<ChampionTag> ENGAGE_SURVIVAL_ENEMY_TAGS = Set.of(
            ChampionTag.TANK,
            ChampionTag.SUPPORT
    );

    /**
     * 실제 관측된 빌드 군집을 ASSASSIN 빌드 후보로 변환한다.
     *
     * <p>대표 방향을 하나로 결정할 수 없는 군집은 후보에 포함하지 않는다.
     */
    @Override
    public ChampionTag supportedTag() {
        return ChampionTag.ASSASSIN;
    }

    @Override
    public List<BuildDirection> supportedDirections() {
        return List.of(
                new BuildDirection(ChampionTag.ASSASSIN, BURST_ASSASSINATION),
                new BuildDirection(ChampionTag.ASSASSIN, DEFENSE_NEUTRALIZATION),
                new BuildDirection(ChampionTag.ASSASSIN, ENGAGE_SURVIVAL)
        );
    }

    @Override
    public List<BuildCandidate> evaluate(
            List<CoreBuildCluster> clusters,
            List<Champion> enemies
    ) {
        Objects.requireNonNull(clusters, "빌드 군집 목록은 null일 수 없습니다.");
        Objects.requireNonNull(enemies, "적 챔피언 목록은 null일 수 없습니다.");

        int burstAssassinationThreat = countThreat(enemies, BURST_ASSASSINATION_ENEMY_TAGS);
        int defenseNeutralizationThreat = countThreat(enemies, DEFENSE_NEUTRALIZATION_ENEMY_TAGS);
        int engageSurvivalThreat = countThreat(enemies, ENGAGE_SURVIVAL_ENEMY_TAGS);

        return clusters.stream()
                .map(cluster -> toCandidate(
                        cluster,
                        burstAssassinationThreat,
                        defenseNeutralizationThreat,
                        engageSurvivalThreat
                ))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<BuildCandidate> toCandidate(
            CoreBuildCluster cluster,
            int burstAssassinationThreat,
            int defenseNeutralizationThreat,
            int engageSurvivalThreat
    ) {
        Objects.requireNonNull(cluster, "빌드 군집은 null일 수 없습니다.");

        List<Item> coreItems = cluster.getRepresentativeSequence().getOrderedItems();
        int burstAssassinationScore = countBurstAssassinationScore(coreItems);
        int defenseNeutralizationScore = countDefenseNeutralizationScore(coreItems);
        int engageSurvivalScore = countEngageSurvivalScore(coreItems);

        Optional<String> directionCode = selectRepresentativeDirection(
                burstAssassinationScore,
                defenseNeutralizationScore,
                engageSurvivalScore
        );

        if (directionCode.isEmpty()) {
            return Optional.empty();
        }

        double suitabilityScore = calculateSuitabilityScore(
                directionCode.get(),
                burstAssassinationScore,
                defenseNeutralizationScore,
                engageSurvivalScore,
                burstAssassinationThreat,
                defenseNeutralizationThreat,
                engageSurvivalThreat
        );

        return Optional.of(new BuildCandidate(
                new BuildDirection(ChampionTag.ASSASSIN, directionCode.get()),
                cluster,
                suitabilityScore
        ));
    }

    private int countBurstAssassinationScore(List<Item> items) {
        return items.stream()
                .mapToInt(item -> score(item, DAMAGE_TAG)
                        + score(item, SPELL_DAMAGE_TAG)
                        + score(item, ARMOR_PENETRATION_TAG)
                        + score(item, MAGIC_PENETRATION_TAG)
                        + score(item, NONBOOTS_MOVEMENT_TAG))
                .sum();
    }

    private int countDefenseNeutralizationScore(List<Item> items) {
        return items.stream()
                .mapToInt(item -> score(item, ARMOR_PENETRATION_TAG)
                        + score(item, MAGIC_PENETRATION_TAG)
                        + score(item, ON_HIT_TAG)
                        + score(item, ATTACK_SPEED_TAG)
                        + score(item, ABILITY_HASTE_TAG))
                .sum();
    }

    private int countEngageSurvivalScore(List<Item> items) {
        return items.stream()
                .mapToInt(item -> score(item, HEALTH_TAG)
                        + score(item, ARMOR_TAG)
                        + score(item, SPELL_BLOCK_TAG, MAGIC_RESIST_TAG)
                        + score(item, TENACITY_TAG)
                        + score(item, LIFE_STEAL_TAG)
                        + score(item, SPELL_VAMP_TAG))
                .sum();
    }

    private int score(Item item, String... sameMeaningTags) {
        for (String tag : sameMeaningTags) {
            if (item.hasTag(tag)) {
                return 1;
            }
        }
        return 0;
    }

    private Optional<String> selectRepresentativeDirection(
            int burstAssassinationScore,
            int defenseNeutralizationScore,
            int engageSurvivalScore
    ) {
        int highestScore = Math.max(
                burstAssassinationScore,
                Math.max(defenseNeutralizationScore, engageSurvivalScore)
        );

        if (highestScore == 0) {
            return Optional.empty();
        }

        int highestDirectionCount = 0;
        highestDirectionCount += burstAssassinationScore == highestScore ? 1 : 0;
        highestDirectionCount += defenseNeutralizationScore == highestScore ? 1 : 0;
        highestDirectionCount += engageSurvivalScore == highestScore ? 1 : 0;

        // 최고점 방향이 여러 개면 임의의 대표 방향을 선택하지 않는다.
        if (highestDirectionCount > 1) {
            return Optional.empty();
        }

        if (burstAssassinationScore == highestScore) {
            return Optional.of(BURST_ASSASSINATION);
        }
        if (defenseNeutralizationScore == highestScore) {
            return Optional.of(DEFENSE_NEUTRALIZATION);
        }
        return Optional.of(ENGAGE_SURVIVAL);
    }

    private double calculateSuitabilityScore(
            String directionCode,
            int burstAssassinationScore,
            int defenseNeutralizationScore,
            int engageSurvivalScore,
            int burstAssassinationThreat,
            int defenseNeutralizationThreat,
            int engageSurvivalThreat
    ) {
        // 초기 기준은 관련 아이템 태그와 적 챔피언 태그에 동일한 가중치를 적용한다.
        return switch (directionCode) {
            case BURST_ASSASSINATION -> burstAssassinationScore * (burstAssassinationThreat + 1);
            case DEFENSE_NEUTRALIZATION ->
                    defenseNeutralizationScore * (defenseNeutralizationThreat + 1);
            case ENGAGE_SURVIVAL -> engageSurvivalScore * (engageSurvivalThreat + 1);
            default -> throw new IllegalArgumentException("알 수 없는 ASSASSIN 빌드 방향입니다.");
        };
    }

    private int countThreat(List<Champion> enemies, Set<ChampionTag> threatTags) {
        // 대표 태그 하나만 고르지 않고 각 적 챔피언이 가진 모든 태그를 확인한다.
        return (int) enemies.stream()
                .flatMap(enemy -> enemy.getChampionTags().stream())
                .filter(threatTags::contains)
                .count();
    }
}
