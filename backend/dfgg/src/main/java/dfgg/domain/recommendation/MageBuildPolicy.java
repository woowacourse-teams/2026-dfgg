package dfgg.domain.recommendation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * MAGE 챔피언의 첫 3코어 군집을 대표 방향으로 분류하고,
 * 적 챔피언 조합에 대한 적합도를 계산한다.
 */
public final class MageBuildPolicy implements ChampionBuildPolicy {

    private static final String BURST_DAMAGE = "BURST_DAMAGE";
    private static final String SUSTAINED_DAMAGE = "SUSTAINED_DAMAGE";
    private static final String SURVIVAL_RESPONSE = "SURVIVAL_RESPONSE";

    private static final String SPELL_DAMAGE_TAG = "SpellDamage";
    private static final String MAGIC_PENETRATION_TAG = "MagicPenetration";
    private static final String ABILITY_HASTE_TAG = "AbilityHaste";
    private static final String COOLDOWN_REDUCTION_TAG = "CooldownReduction";
    private static final String MANA_TAG = "Mana";
    private static final String SPELL_VAMP_TAG = "SpellVamp";
    private static final String HEALTH_TAG = "Health";
    private static final String ARMOR_TAG = "Armor";
    private static final String SPELL_BLOCK_TAG = "SpellBlock";
    private static final String MAGIC_RESIST_TAG = "MagicResist";
    private static final String TENACITY_TAG = "Tenacity";
    private static final String NONBOOTS_MOVEMENT_TAG = "NonbootsMovement";

    private static final int BURST_DAMAGE_CRITERIA_COUNT = 1;
    private static final int SUSTAINED_DAMAGE_CRITERIA_COUNT = 3;
    private static final int SURVIVAL_RESPONSE_CRITERIA_COUNT = 5;

    private static final Set<ChampionTag> BURST_DAMAGE_ENEMY_TAGS = Set.of(
            ChampionTag.MAGE,
            ChampionTag.MARKSMAN
    );

    private static final Set<ChampionTag> SUSTAINED_DAMAGE_ENEMY_TAGS = Set.of(
            ChampionTag.TANK,
            ChampionTag.FIGHTER
    );

    private static final Set<ChampionTag> SURVIVAL_RESPONSE_ENEMY_TAGS = Set.of(
            ChampionTag.ASSASSIN
    );

    /**
     * 실제 관측된 빌드 군집을 MAGE 빌드 후보로 변환한다.
     *
     * <p>SpellDamage 태그가 없거나 대표 방향을 하나로 결정할 수 없는 군집은
     * 후보에 포함하지 않는다.
     */
    @Override
    public ChampionTag supportedTag() {
        return ChampionTag.MAGE;
    }

    @Override
    public List<BuildDirection> supportedDirections() {
        return List.of(
                new BuildDirection(ChampionTag.MAGE, BURST_DAMAGE),
                new BuildDirection(ChampionTag.MAGE, SUSTAINED_DAMAGE),
                new BuildDirection(ChampionTag.MAGE, SURVIVAL_RESPONSE)
        );
    }

    @Override
    public List<BuildCandidate> evaluate(
            List<CoreBuildCluster> clusters,
            List<Champion> enemies
    ) {
        Objects.requireNonNull(clusters, "빌드 군집 목록은 null일 수 없습니다.");
        Objects.requireNonNull(enemies, "적 챔피언 목록은 null일 수 없습니다.");

        int burstDamageThreat = countThreat(enemies, BURST_DAMAGE_ENEMY_TAGS);
        int sustainedDamageThreat = countThreat(enemies, SUSTAINED_DAMAGE_ENEMY_TAGS);
        int survivalResponseThreat = countThreat(enemies, SURVIVAL_RESPONSE_ENEMY_TAGS);

        return clusters.stream()
                .map(cluster -> toCandidate(
                        cluster,
                        burstDamageThreat,
                        sustainedDamageThreat,
                        survivalResponseThreat
                ))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<BuildCandidate> toCandidate(
            CoreBuildCluster cluster,
            int burstDamageThreat,
            int sustainedDamageThreat,
            int survivalResponseThreat
    ) {
        Objects.requireNonNull(cluster, "빌드 군집은 null일 수 없습니다.");

        List<Item> coreItems = cluster.getRepresentativeSequence().getOrderedItems();
        if (coreItems.stream().noneMatch(item -> item.hasTag(SPELL_DAMAGE_TAG))) {
            return Optional.empty();
        }

        double burstDamageScore = normalize(
                countBurstDamageScore(coreItems),
                coreItems.size(),
                BURST_DAMAGE_CRITERIA_COUNT
        );
        double sustainedDamageScore = normalize(
                countSustainedDamageScore(coreItems),
                coreItems.size(),
                SUSTAINED_DAMAGE_CRITERIA_COUNT
        );
        double survivalResponseScore = normalize(
                countSurvivalResponseScore(coreItems),
                coreItems.size(),
                SURVIVAL_RESPONSE_CRITERIA_COUNT
        );

        Optional<String> directionCode = selectRepresentativeDirection(
                burstDamageScore,
                sustainedDamageScore,
                survivalResponseScore
        );

        if (directionCode.isEmpty()) {
            return Optional.empty();
        }

        double suitabilityScore = calculateSuitabilityScore(
                directionCode.get(),
                burstDamageScore,
                sustainedDamageScore,
                survivalResponseScore,
                burstDamageThreat,
                sustainedDamageThreat,
                survivalResponseThreat
        );

        return Optional.of(new BuildCandidate(
                new BuildDirection(ChampionTag.MAGE, directionCode.get()),
                cluster,
                suitabilityScore
        ));
    }

    private int countBurstDamageScore(List<Item> items) {
        // 서로 다른 아이템의 태그를 합쳐 순간 화력으로 판단하지 않는다.
        return (int) items.stream()
                .filter(item -> item.hasTag(SPELL_DAMAGE_TAG))
                .filter(item -> item.hasTag(MAGIC_PENETRATION_TAG))
                .count();
    }

    private int countSustainedDamageScore(List<Item> items) {
        return items.stream()
                .mapToInt(item -> score(item, ABILITY_HASTE_TAG, COOLDOWN_REDUCTION_TAG)
                        + score(item, MANA_TAG)
                        + score(item, SPELL_VAMP_TAG))
                .sum();
    }

    private int countSurvivalResponseScore(List<Item> items) {
        return items.stream()
                .mapToInt(item -> score(item, HEALTH_TAG)
                        + score(item, ARMOR_TAG)
                        + score(item, SPELL_BLOCK_TAG, MAGIC_RESIST_TAG)
                        + score(item, TENACITY_TAG)
                        + score(item, NONBOOTS_MOVEMENT_TAG))
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

    private double normalize(int rawScore, int coreItemCount, int criteriaCount) {
        return (double) rawScore / (coreItemCount * criteriaCount);
    }

    private Optional<String> selectRepresentativeDirection(
            double burstDamageScore,
            double sustainedDamageScore,
            double survivalResponseScore
    ) {
        double highestScore = Math.max(
                burstDamageScore,
                Math.max(sustainedDamageScore, survivalResponseScore)
        );

        if (Double.compare(highestScore, 0.0) == 0) {
            return Optional.empty();
        }

        int highestDirectionCount = 0;
        highestDirectionCount += Double.compare(burstDamageScore, highestScore) == 0 ? 1 : 0;
        highestDirectionCount += Double.compare(sustainedDamageScore, highestScore) == 0 ? 1 : 0;
        highestDirectionCount += Double.compare(survivalResponseScore, highestScore) == 0 ? 1 : 0;

        // 최고점 방향이 여러 개면 임의의 대표 방향을 선택하지 않는다.
        if (highestDirectionCount > 1) {
            return Optional.empty();
        }

        if (Double.compare(burstDamageScore, highestScore) == 0) {
            return Optional.of(BURST_DAMAGE);
        }
        if (Double.compare(sustainedDamageScore, highestScore) == 0) {
            return Optional.of(SUSTAINED_DAMAGE);
        }
        return Optional.of(SURVIVAL_RESPONSE);
    }

    private double calculateSuitabilityScore(
            String directionCode,
            double burstDamageScore,
            double sustainedDamageScore,
            double survivalResponseScore,
            int burstDamageThreat,
            int sustainedDamageThreat,
            int survivalResponseThreat
    ) {
        // 초기 기준은 정규화한 빌드 성격 점수와 관련 적 태그 수를 함께 반영한다.
        return switch (directionCode) {
            case BURST_DAMAGE -> burstDamageScore * (burstDamageThreat + 1);
            case SUSTAINED_DAMAGE -> sustainedDamageScore * (sustainedDamageThreat + 1);
            case SURVIVAL_RESPONSE -> survivalResponseScore * (survivalResponseThreat + 1);
            default -> throw new IllegalArgumentException("알 수 없는 MAGE 빌드 방향입니다.");
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
