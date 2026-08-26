package dfgg.domain.recommendation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemTrait;
import dfgg.domain.item.ItemTraitCatalog;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * SUPPORT 챔피언의 첫 3코어 군집을 대표 방향으로 분류하고,
 * 적 챔피언 조합에 대한 적합도를 계산한다.
 */
public final class SupportBuildPolicy implements ChampionBuildPolicy {

    private static final String ENGAGE_INITIATION = "ENGAGE_INITIATION";
    private static final String ALLY_PROTECTION = "ALLY_PROTECTION";
    private static final String HEALING_ENHANCEMENT = "HEALING_ENHANCEMENT";

    private static final String HEALTH_TAG = "Health";
    private static final String ARMOR_TAG = "Armor";
    private static final String SPELL_BLOCK_TAG = "SpellBlock";
    private static final String MAGIC_RESIST_TAG = "MagicResist";
    private static final String TENACITY_TAG = "Tenacity";
    private static final String NONBOOTS_MOVEMENT_TAG = "NonbootsMovement";
    private static final String SLOW_TAG = "Slow";
    private static final String ACTIVE_TAG = "Active";
    private static final String ABILITY_HASTE_TAG = "AbilityHaste";
    private static final String COOLDOWN_REDUCTION_TAG = "CooldownReduction";
    private static final String AURA_TAG = "Aura";
    private static final String MANA_REGEN_TAG = "ManaRegen";
    private static final String HEALTH_REGEN_TAG = "HealthRegen";
    private static final String SPELL_DAMAGE_TAG = "SpellDamage";

    private static final int ITEM_TAG_WEIGHT = 1;
    private static final int ITEM_TRAIT_WEIGHT = 2;

    private static final Set<ChampionTag> ENGAGE_ENEMY_TAGS = Set.of(
            ChampionTag.MAGE,
            ChampionTag.MARKSMAN
    );

    private static final Set<ChampionTag> ALLY_PROTECTION_ENEMY_TAGS = Set.of(
            ChampionTag.ASSASSIN,
            ChampionTag.FIGHTER
    );

    private static final Set<ChampionTag> HEALING_ENHANCEMENT_ENEMY_TAGS = Set.of(
            ChampionTag.MAGE,
            ChampionTag.TANK,
            ChampionTag.FIGHTER
    );

    private final ItemTraitCatalog traitCatalog;

    public SupportBuildPolicy() {
        this(new ItemTraitCatalog());
    }

    public SupportBuildPolicy(ItemTraitCatalog traitCatalog) {
        this.traitCatalog = Objects.requireNonNull(
                traitCatalog,
                "아이템 trait 카탈로그는 null일 수 없습니다."
        );
    }

    /**
     * 실제 관측된 빌드 군집을 SUPPORT 빌드 후보로 변환한다.
     *
     * <p>대표 방향을 하나로 결정할 수 없는 군집은 후보에 포함하지 않는다.
     */
    @Override
    public ChampionTag supportedTag() {
        return ChampionTag.SUPPORT;
    }

    @Override
    public List<BuildCandidate> evaluate(
            List<CoreBuildCluster> clusters,
            List<Champion> enemies
    ) {
        Objects.requireNonNull(clusters, "빌드 군집 목록은 null일 수 없습니다.");
        Objects.requireNonNull(enemies, "적 챔피언 목록은 null일 수 없습니다.");

        int engageThreat = countThreat(enemies, ENGAGE_ENEMY_TAGS);
        int allyProtectionThreat = countThreat(enemies, ALLY_PROTECTION_ENEMY_TAGS);
        int healingEnhancementThreat = countThreat(enemies, HEALING_ENHANCEMENT_ENEMY_TAGS);

        return clusters.stream()
                .map(cluster -> toCandidate(
                        cluster,
                        engageThreat,
                        allyProtectionThreat,
                        healingEnhancementThreat
                ))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<BuildCandidate> toCandidate(
            CoreBuildCluster cluster,
            int engageThreat,
            int allyProtectionThreat,
            int healingEnhancementThreat
    ) {
        Objects.requireNonNull(cluster, "빌드 군집은 null일 수 없습니다.");

        List<Item> coreItems = cluster.getRepresentativeSequence().getOrderedItems();
        int engageScore = countEngageScore(coreItems);
        int allyProtectionScore = countAllyProtectionScore(coreItems);
        int healingEnhancementScore = countHealingEnhancementScore(coreItems);

        Optional<String> directionCode = selectRepresentativeDirection(
                engageScore,
                allyProtectionScore,
                healingEnhancementScore
        );

        if (directionCode.isEmpty()) {
            return Optional.empty();
        }

        double suitabilityScore = calculateSuitabilityScore(
                directionCode.get(),
                engageScore,
                allyProtectionScore,
                healingEnhancementScore,
                engageThreat,
                allyProtectionThreat,
                healingEnhancementThreat
        );

        return Optional.of(new BuildCandidate(
                new BuildDirection(ChampionTag.SUPPORT, directionCode.get()),
                cluster,
                suitabilityScore
        ));
    }

    private int countEngageScore(List<Item> items) {
        return items.stream()
                .mapToInt(item -> tagScore(item, HEALTH_TAG)
                        + tagScore(item, ARMOR_TAG)
                        + tagScore(item, SPELL_BLOCK_TAG, MAGIC_RESIST_TAG)
                        + tagScore(item, TENACITY_TAG)
                        + tagScore(item, NONBOOTS_MOVEMENT_TAG)
                        + tagScore(item, SLOW_TAG)
                        + tagScore(item, ACTIVE_TAG)
                        + traitScore(item, ItemTrait.ENGAGE))
                .sum();
    }

    private int countAllyProtectionScore(List<Item> items) {
        return items.stream()
                .mapToInt(item -> tagScore(item, ABILITY_HASTE_TAG, COOLDOWN_REDUCTION_TAG)
                        + tagScore(item, SLOW_TAG)
                        + tagScore(item, AURA_TAG)
                        + tagScore(item, HEALTH_TAG)
                        + tagScore(item, ARMOR_TAG)
                        + tagScore(item, SPELL_BLOCK_TAG, MAGIC_RESIST_TAG)
                        + tagScore(item, ACTIVE_TAG)
                        + traitScore(item, ItemTrait.PEEL))
                .sum();
    }

    private int countHealingEnhancementScore(List<Item> items) {
        return items.stream()
                .mapToInt(item -> tagScore(item, MANA_REGEN_TAG)
                        + tagScore(item, HEALTH_REGEN_TAG)
                        + tagScore(item, ABILITY_HASTE_TAG, COOLDOWN_REDUCTION_TAG)
                        + tagScore(item, AURA_TAG)
                        + tagScore(item, SPELL_DAMAGE_TAG)
                        + traitScore(item, ItemTrait.HEAL)
                        + traitScore(item, ItemTrait.SHIELD)
                        + traitScore(item, ItemTrait.TEAM_BUFF))
                .sum();
    }

    private int tagScore(Item item, String... sameMeaningTags) {
        for (String tag : sameMeaningTags) {
            if (item.hasTag(tag)) {
                return ITEM_TAG_WEIGHT;
            }
        }
        return 0;
    }

    private int traitScore(Item item, ItemTrait trait) {
        return traitCatalog.hasTrait(item, trait) ? ITEM_TRAIT_WEIGHT : 0;
    }

    private Optional<String> selectRepresentativeDirection(
            int engageScore,
            int allyProtectionScore,
            int healingEnhancementScore
    ) {
        int highestScore = Math.max(
                engageScore,
                Math.max(allyProtectionScore, healingEnhancementScore)
        );

        if (highestScore == 0) {
            return Optional.empty();
        }

        int highestDirectionCount = 0;
        highestDirectionCount += engageScore == highestScore ? 1 : 0;
        highestDirectionCount += allyProtectionScore == highestScore ? 1 : 0;
        highestDirectionCount += healingEnhancementScore == highestScore ? 1 : 0;

        // 최고점 방향이 여러 개면 임의의 대표 방향을 선택하지 않는다.
        if (highestDirectionCount > 1) {
            return Optional.empty();
        }

        if (engageScore == highestScore) {
            return Optional.of(ENGAGE_INITIATION);
        }
        if (allyProtectionScore == highestScore) {
            return Optional.of(ALLY_PROTECTION);
        }
        return Optional.of(HEALING_ENHANCEMENT);
    }

    private double calculateSuitabilityScore(
            String directionCode,
            int engageScore,
            int allyProtectionScore,
            int healingEnhancementScore,
            int engageThreat,
            int allyProtectionThreat,
            int healingEnhancementThreat
    ) {
        return switch (directionCode) {
            case ENGAGE_INITIATION -> engageScore * (engageThreat + 1);
            case ALLY_PROTECTION -> allyProtectionScore * (allyProtectionThreat + 1);
            case HEALING_ENHANCEMENT ->
                    healingEnhancementScore * (healingEnhancementThreat + 1);
            default -> throw new IllegalArgumentException("알 수 없는 SUPPORT 빌드 방향입니다.");
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
