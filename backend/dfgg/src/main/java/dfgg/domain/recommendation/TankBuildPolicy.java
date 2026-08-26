package dfgg.domain.recommendation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * TANK 챔피언의 첫 3코어 군집을 방어 방향으로 분류하고,
 * 적 챔피언 조합에 대한 적합도를 계산한다.
 */
public final class TankBuildPolicy {

    private static final String ARMOR_TAG = "Armor";
    private static final String SPELL_BLOCK_TAG = "SpellBlock";
    private static final String HEALTH_TAG = "Health";

    private static final String PHYSICAL_DAMAGE = "PHYSICAL_DAMAGE";
    private static final String MAGIC_DAMAGE = "MAGIC_DAMAGE";
    private static final String MIXED_DAMAGE = "MIXED_DAMAGE";

    private static final Set<ChampionTag> PHYSICAL_THREAT_TAGS = Set.of(
            ChampionTag.FIGHTER,
            ChampionTag.ASSASSIN,
            ChampionTag.MARKSMAN
    );

    private static final Set<ChampionTag> MAGIC_THREAT_TAGS = Set.of(
            ChampionTag.MAGE
    );

    /**
     * 실제 관측된 빌드 군집을 TANK 빌드 후보로 변환한다.
     *
     * <p>방어 방향을 판단할 수 없는 군집은 후보에 포함하지 않는다.
     */
    public List<BuildCandidate> evaluate(
            List<CoreBuildCluster> clusters,
            List<Champion> enemies
    ) {
        Objects.requireNonNull(clusters, "빌드 군집 목록은 null일 수 없습니다.");
        Objects.requireNonNull(enemies, "적 챔피언 목록은 null일 수 없습니다.");

        int physicalThreat = countThreat(enemies, PHYSICAL_THREAT_TAGS);
        int magicThreat = countThreat(enemies, MAGIC_THREAT_TAGS);

        return clusters.stream()
                .map(cluster -> toCandidate(cluster, physicalThreat, magicThreat))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<BuildCandidate> toCandidate(
            CoreBuildCluster cluster,
            int physicalThreat,
            int magicThreat
    ) {
        Objects.requireNonNull(cluster, "빌드 군집은 null일 수 없습니다.");

        List<Item> coreItems = cluster.getRepresentativeSequence().getOrderedItems();
        int armorCount = countTag(coreItems, ARMOR_TAG);
        int spellBlockCount = countTag(coreItems, SPELL_BLOCK_TAG);
        int healthCount = countTag(coreItems, HEALTH_TAG);

        // Armor와 SpellBlock 태그 수가 같은 경우에만 혼합 피해 대응으로 분류한다.
        String directionCode;
        if (armorCount > spellBlockCount) {
            directionCode = PHYSICAL_DAMAGE;
        } else if (spellBlockCount > armorCount) {
            directionCode = MAGIC_DAMAGE;
        } else if (armorCount == 0) {
            // Health는 공통 생존 능력이라 피해 유형을 단독으로 결정할 수 없다.
            return Optional.empty();
        } else {
            directionCode = MIXED_DAMAGE;
        }

        double suitabilityScore = calculateScore(
                directionCode,
                armorCount,
                spellBlockCount,
                healthCount,
                physicalThreat,
                magicThreat
        );

        return Optional.of(new BuildCandidate(
                new BuildDirection(ChampionTag.TANK, directionCode),
                cluster,
                suitabilityScore
        ));
    }

    private double calculateScore(
            String directionCode,
            int armorCount,
            int spellBlockCount,
            int healthCount,
            int physicalThreat,
            int magicThreat
    ) {
        // 방향과 일치하는 적 위협을 중심으로 계산하고 Health는 공통 생존 점수로 반영한다.
        return switch (directionCode) {
            case PHYSICAL_DAMAGE -> armorCount * (physicalThreat + 1) + healthCount;
            case MAGIC_DAMAGE -> spellBlockCount * (magicThreat + 1) + healthCount;
            case MIXED_DAMAGE -> armorCount * physicalThreat
                    + spellBlockCount * magicThreat
                    + healthCount;
            default -> throw new IllegalArgumentException("알 수 없는 TANK 빌드 방향입니다.");
        };
    }

    private int countTag(List<Item> items, String tag) {
        return (int) items.stream()
                .filter(item -> item.hasTag(tag))
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
