package dfgg.application.stats;

import dfgg.application.item.ItemService;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.Item;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CombinationContext;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import dfgg.domain.team.Team;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정규화된 한 매치에서 참가자별 조합과 아이템 빌드 통계를 계산해 저장한다.
 */
@Service
public class ChampionBuildStatsAggregationService {

    private final ChampionBuildStatsRepository statsRepository;
    private final CompositionStatsSampleRepository sampleRepository;
    private final ChampionRepository championRepository;
    private final ItemService itemService;

    public ChampionBuildStatsAggregationService(
            ChampionBuildStatsRepository statsRepository,
            CompositionStatsSampleRepository sampleRepository,
            ChampionRepository championRepository,
            ItemService itemService
    ) {
        this.statsRepository = statsRepository;
        this.sampleRepository = sampleRepository;
        this.championRepository = championRepository;
        this.itemService = itemService;
    }

    /**
     * 매치의 대상 참가자별로 빌드 통계를 만들고, 실제 표본이 처음 들어오는 경우에만 집계 수를 증가시킨다.
     *
     * <p>한 참가자는 조합 조건의 구체적인 값과 전체 조건을 함께 조회할 수 있도록 여러 통계 표본으로 확장된다.
     */
    @Transactional
    public void aggregate(
            NormalizedMatch match,
            String tier,
            Collection<String> participantPuuids
    ) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        Set<String> targets = Set.copyOf(participantPuuids);
        Map<Integer, Champion> champions = loadChampions(match);
        Map<Long, Item> items = loadItems(match);

        // 구매 순서를 완전히 복원한 대상 참가자만 통계에 포함한다.
        for (NormalizedParticipant participant : match.participants()) {
            if (!isTargetParticipant(participant, targets)
                    || !participant.coreItemPurchaseOrderComplete()
                    || participant.coreItemPurchaseOrder().isEmpty()) {
                continue;
            }

            Champion champion = champions.get(participant.championId());
            ChampionPosition position = parsePosition(participant.position()).orElse(null);
            List<Item> buildItems = itemList(participant.coreItemPurchaseOrder(), items);
            if (champion == null || position == null || buildItems == null) {
                continue;
            }

            CombinationContext context = combinationContext(match, participant, champions).orElse(null);
            if (context == null) {
                continue;
            }

            String buildKey = participant.coreItemPurchaseOrder().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(">"));
            for (ContextVariant variant : contextVariants(context)) {
                recordSample(
                        match,
                        participant,
                        tier,
                        champion,
                        position,
                        variant,
                        buildKey,
                        buildItems
                );
            }
        }
    }

    /**
     * 하나의 조합 조건에 대한 통계 행과 참가자 표본을 멱등적으로 저장한다.
     */
    private void recordSample(
            NormalizedMatch match,
            NormalizedParticipant participant,
            String tier,
            Champion champion,
            ChampionPosition position,
            ContextVariant variant,
            String buildKey,
            List<Item> buildItems
    ) {
        String statsKey = ChampionBuildStats.createStatsKey(
                match.patch(),
                match.queueId(),
                champion,
                position,
                variant.enemyTankHeavy(),
                variant.enemyApHeavy(),
                variant.enemyAssassinHeavy(),
                variant.allyHasMarksman(),
                variant.allyTankHeavy(),
                tier,
                buildKey
        );
        // 통계 조건은 한 번만 만들고, 처음 기록되는 표본일 때만 게임 수와 승수를 증가시킨다.
        int insertedStats = statsRepository.insertIfAbsent(
                match.patch(),
                match.queueId(),
                champion.getChampionId(),
                position.name(),
                variant.enemyTankHeavy(),
                variant.enemyApHeavy(),
                variant.enemyAssassinHeavy(),
                variant.allyHasMarksman(),
                variant.allyTankHeavy(),
                tier,
                buildKey,
                statsKey
        );
        if (insertedStats == 1) {
            for (int itemOrder = 0; itemOrder < buildItems.size(); itemOrder++) {
                statsRepository.insertItem(statsKey, buildItems.get(itemOrder).getItemId(), itemOrder);
            }
        }
        sampleRepository.insertAndIncrementIfAbsent(
                statsKey,
                match.matchId(),
                participant.puuid(),
                participant.win()
        );
    }

    /**
     * 매치에 포함된 챔피언 ID만 조회해 챔피언 ID로 빠르게 찾을 수 있는 맵으로 만든다.
     */
    private Map<Integer, Champion> loadChampions(NormalizedMatch match) {
        List<Long> championIds = match.participants().stream()
                .map(NormalizedParticipant::championId)
                .distinct()
                .map(Integer::longValue)
                .toList();
        return championRepository.findAllById(championIds).stream()
                .collect(Collectors.toMap(
                        champion -> champion.getChampionId().intValue(),
                        champion -> champion
                ));
    }

    /**
     * 매치 참가자들의 구매 순서에 등장하는 아이템만 조회해 아이템 ID 맵으로 만든다.
     */
    private Map<Long, Item> loadItems(NormalizedMatch match) {
        List<Long> itemIds = match.participants().stream()
                .flatMap(participant -> participant.coreItemPurchaseOrder().stream())
                .distinct()
                .map(Integer::longValue)
                .toList();
        return itemService.findItemsByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, item -> item));
    }

    /**
     * 현재 참가자가 이번 집계 대상 PUUID에 포함되는지 확인한다.
     */
    private boolean isTargetParticipant(NormalizedParticipant participant, Set<String> targets) {
        return targets.contains(participant.puuid());
    }

    /**
     * 외부 데이터의 포지션 문자열을 애플리케이션의 포지션 enum으로 변환한다.
     */
    private Optional<ChampionPosition> parsePosition(String position) {
        if (position == null || position.isBlank()) {
            return Optional.empty();
        }

        String normalizedPosition = position.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedPosition) {
            case "MIDDLE" -> Optional.of(ChampionPosition.MID);
            case "UTILITY" -> Optional.of(ChampionPosition.SUPPORT);
            default -> {
                try {
                    yield Optional.of(ChampionPosition.valueOf(normalizedPosition));
                } catch (IllegalArgumentException exception) {
                    yield Optional.empty();
                }
            }
        };
    }

    /**
     * 구매 순서의 아이템 ID를 실제 아이템 객체 목록으로 변환한다.
     * 하나라도 메타데이터가 없으면 해당 참가자의 표본을 만들 수 없으므로 null을 반환한다.
     */
    private List<Item> itemList(List<Integer> itemIds, Map<Long, Item> items) {
        List<Item> result = new ArrayList<>();
        for (Integer itemId : itemIds) {
            Item item = items.get(itemId.longValue());
            if (item == null) {
                return null;
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 대상 참가자를 기준으로 같은 팀과 상대 팀의 챔피언을 나눠 조합 조건을 계산한다.
     */
    private Optional<CombinationContext> combinationContext(
            NormalizedMatch match,
            NormalizedParticipant participant,
            Map<Integer, Champion> champions
    ) {
        List<Champion> allies = new ArrayList<>();
        List<Champion> enemies = new ArrayList<>();
        for (NormalizedParticipant other : match.participants()) {
            Champion otherChampion = champions.get(other.championId());
            if (otherChampion == null) {
                return Optional.empty();
            }
            if (Objects.equals(other.puuid(), participant.puuid())) {
                continue;
            }
            if (Objects.equals(other.teamId(), participant.teamId())) {
                allies.add(otherChampion);
            } else {
                enemies.add(otherChampion);
            }
        }
        return Optional.of(CombinationContext.analyze(new Team(enemies), new Team(allies)));
    }

    /**
     * 실제 조합 조건에서 일부 조건을 전체 범위로 확장한 32개 검색 변형을 만든다.
     */
    private List<ContextVariant> contextVariants(CombinationContext context) {
        // 다섯 조건을 실제 값 또는 전체 조건(null)으로 조합해 검색 범위별 통계를 함께 만든다.
        boolean[] values = {
                context.enemyTankHeavy(),
                context.enemyApHeavy(),
                context.enemyAssassinHeavy(),
                context.allyHasMarksman(),
                context.allyTankHeavy()
        };
        return IntStream.range(0, 1 << values.length)
                .mapToObj(mask -> new ContextVariant(
                        valueOrNull(values[0], mask, 0),
                        valueOrNull(values[1], mask, 1),
                        valueOrNull(values[2], mask, 2),
                        valueOrNull(values[3], mask, 3),
                        valueOrNull(values[4], mask, 4)
                ))
                .toList();
    }

    /**
     * 비트 마스크가 해당 조건을 포함하면 실제 값을, 아니면 전체 조건을 뜻하는 null을 반환한다.
     */
    private Boolean valueOrNull(boolean value, int mask, int bit) {
        return (mask & (1 << bit)) == 0 ? null : value;
    }

    private record ContextVariant(
            Boolean enemyTankHeavy,
            Boolean enemyApHeavy,
            Boolean enemyAssassinHeavy,
            Boolean allyHasMarksman,
            Boolean allyTankHeavy
    ) {
    }
}
