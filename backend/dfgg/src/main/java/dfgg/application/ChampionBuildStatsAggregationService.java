package dfgg.application;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
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

@Service
public class ChampionBuildStatsAggregationService {

    private final ChampionBuildStatsRepository statsRepository;
    private final CompositionStatsSampleRepository sampleRepository;
    private final ChampionRepository championRepository;
    private final ItemRepository itemRepository;

    public ChampionBuildStatsAggregationService(
            ChampionBuildStatsRepository statsRepository,
            CompositionStatsSampleRepository sampleRepository,
            ChampionRepository championRepository,
            ItemRepository itemRepository
    ) {
        this.statsRepository = statsRepository;
        this.sampleRepository = sampleRepository;
        this.championRepository = championRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public int aggregate(
            NormalizedMatch match,
            String tier,
            Collection<String> cohortPuuids
    ) {
        Objects.requireNonNull(match, "match must not be null");
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        Set<String> cohort = Set.copyOf(Objects.requireNonNull(cohortPuuids, "cohortPuuids must not be null"));
        Map<Integer, Champion> champions = loadChampions(match);
        Map<Long, Item> items = loadItems(match);
        int recordedSamples = 0;

        for (NormalizedParticipant participant : match.participants()) {
            if (!isTargetParticipant(participant, cohort)
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
                recordedSamples += recordSample(
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
        return recordedSamples;
    }

    private int recordSample(
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
        ChampionBuildStats stats = statsRepository.findByStatsKey(statsKey)
                .orElseGet(() -> statsRepository.saveAndFlush(new ChampionBuildStats(
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
                        buildKey,
                        buildItems,
                        0,
                        0
                )));

        int inserted = sampleRepository.insertIfAbsent(statsKey, match.matchId(), participant.puuid());
        if (inserted == 1) {
            stats.recordGame(participant.win());
            statsRepository.save(stats);
        }
        return inserted;
    }

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

    private Map<Long, Item> loadItems(NormalizedMatch match) {
        List<Long> itemIds = match.participants().stream()
                .flatMap(participant -> participant.coreItemPurchaseOrder().stream())
                .distinct()
                .map(Integer::longValue)
                .toList();
        return itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, item -> item));
    }

    private boolean isTargetParticipant(NormalizedParticipant participant, Set<String> cohort) {
        return cohort.contains(participant.puuid());
    }

    private Optional<ChampionPosition> parsePosition(String position) {
        if (position == null || position.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChampionPosition.valueOf(position.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

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

    private List<ContextVariant> contextVariants(CombinationContext context) {
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
