package dfgg.domain.recommendation;

import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CoreBuildCluster {

    private static final String BOOTS_TAG = "Boots";
    private static final int CORE_ITEM_COUNT = 3;

    private final List<Long> clusterKey;
    private final List<ChampionBuildStats> observedStats;
    private final int gameCount;
    private final int winCount;
    private final CoreBuildSequence representativeSequence;

    private CoreBuildCluster(List<Long> clusterKey, List<ChampionBuildStats> observedStats, int gameCount, int winCount,
                             CoreBuildSequence representativeSequence) {
        this.clusterKey = List.copyOf(clusterKey);
        this.observedStats = List.copyOf(observedStats);
        this.gameCount = gameCount;
        this.winCount = winCount;
        this.representativeSequence = representativeSequence;
    }

    public static CoreBuildCluster from(List<Long> clusterKey, List<ChampionBuildStats> observedStats) {
        if (observedStats.isEmpty()) {
            throw new IllegalArgumentException("관측 빌드 통계는 비어 있을 수 없습니다.");
        }
        Map<List<Long>, PurchaseOrderSummary> sequenceSummaries = new LinkedHashMap<>();
        int totalGameCount = 0;
        int totalWinCount = 0;

        for (ChampionBuildStats stats : observedStats) {
            CoreBuildSequence sequence = CoreBuildSequence.from(stats.getItems())
                    .orElseThrow(() -> new IllegalArgumentException("관측 빌드에는 신발을 제외한 코어 아이템 3개가 필요합니다."));
            if (!clusterKey.equals(sequence.clusterKey())) {
                throw new IllegalArgumentException("모든 관측 빌드는 동일한 clusterKey를 가져야 합니다.");
            }

            int games = countOrZero(stats.getGameCount());
            int wins = countOrZero(stats.getWinCount());

            totalGameCount += games;
            totalWinCount += wins;

            List<Long> orderedCoreKey = orderedCoreKey(sequence);

            sequenceSummaries.merge(
                    orderedCoreKey,
                    new PurchaseOrderSummary(sequence, games, wins),
                    PurchaseOrderSummary::combine
            );
        }
        // 동률이면 기존 값을 유지하므로 먼저 관측된 순서가 대표가 된다.
        PurchaseOrderSummary representative = sequenceSummaries.values()
                .stream()
                .reduce((current, candidate) ->
                        isBetter(candidate, current) ? candidate : current
                )
                .orElseThrow();

        return new CoreBuildCluster(clusterKey, observedStats, totalGameCount, totalWinCount,
                representative.sequence());
    }

    /**
     * 대표 첫 3코어 순서와 실제 관측 순서가 일치하는 완성 빌드를 찾는다.
     *
     * <p>expectedItemCount에는 신발을 포함한 최종 아이템 개수를 전달한다.
     */
    public Optional<ChampionBuildStats> findRepresentativeBuild(int expectedItemCount) {
        if (expectedItemCount < 1) {
            throw new IllegalArgumentException("완성 빌드 아이템 개수는 1개 이상이어야 합니다.");
        }

        List<Long> representativeOrder = orderedCoreKey(representativeSequence);
        ChampionBuildStats selected = null;

        for (ChampionBuildStats stats : observedStats) {
            if (stats.getItems().size() != expectedItemCount) {
                continue;
            }

            Optional<CoreBuildSequence> sequence =
                    CoreBuildSequence.from(stats.getItems());

            if (sequence.isEmpty()
                    || !representativeOrder.equals(
                    orderedCoreKey(sequence.get())
            )) {
                continue;
            }

            if (selected == null || isBetter(stats, selected)) {
                selected = stats;
            }
        }
        return Optional.ofNullable(selected);
    }

    /**
     * 실제 완성 빌드를 우선 사용하고, 없으면 같은 군집의 관측 통계로 완성한다.
     *
     * <p>fallback은 대표 첫 3코어를 보존하고 가장 많이 관측된 신발과 구매 위치,
     * 첫 3코어 이후 슬롯별 아이템을 gameCount 기준으로 선택한다.
     */
    public Optional<List<Item>> findOrComposeRepresentativeBuild(int expectedItemCount) {
        if (expectedItemCount < CORE_ITEM_COUNT + 1) {
            throw new IllegalArgumentException("완성 빌드는 첫 3코어와 신발을 포함해야 합니다.");
        }

        Optional<List<Item>> observedBuild = findRepresentativeBuild(expectedItemCount)
                .map(ChampionBuildStats::getItems)
                .filter(this::hasExactlyOneBoots);
        if (observedBuild.isPresent()) {
            return observedBuild;
        }
        return composeRepresentativeBuild(expectedItemCount);
    }

    private Optional<List<Item>> composeRepresentativeBuild(int expectedItemCount) {
        Map<Long, ItemSummary> bootsByItem = new LinkedHashMap<>();
        Map<Long, Map<Integer, ItemSummary>> bootPositionsByItem = new LinkedHashMap<>();
        Map<Integer, Map<Long, ItemSummary>> lateItemsByOrder = new LinkedHashMap<>();

        for (ChampionBuildStats stats : observedStats) {
            accumulateBuildObservations(
                    stats,
                    bootsByItem,
                    bootPositionsByItem,
                    lateItemsByOrder
            );
        }

        ItemSummary boots = selectBest(bootsByItem, Set.of());
        if (boots == null) {
            return Optional.empty();
        }
        ItemSummary bootPosition = selectBest(
                bootPositionsByItem.getOrDefault(
                        boots.item().getItemId(),
                        Map.of()
                ),
                Set.of()
        );
        if (bootPosition == null) {
            return Optional.empty();
        }

        List<Item> nonBootItems = new ArrayList<>(
                representativeSequence.getOrderedItems()
        );
        Set<Long> selectedItemIds = new HashSet<>();
        nonBootItems.forEach(item -> selectedItemIds.add(item.getItemId()));
        selectedItemIds.add(boots.item().getItemId());

        int requiredLateItemCount = expectedItemCount - CORE_ITEM_COUNT - 1;
        for (int lateOrder = 0; lateOrder < requiredLateItemCount; lateOrder++) {
            ItemSummary lateItem = selectBest(
                    lateItemsByOrder.getOrDefault(lateOrder, Map.of()),
                    selectedItemIds
            );
            if (lateItem == null) {
                return Optional.empty();
            }
            nonBootItems.add(lateItem.item());
            selectedItemIds.add(lateItem.item().getItemId());
        }

        int bootIndex = Math.min(
                bootPosition.position(),
                nonBootItems.size()
        );
        nonBootItems.add(bootIndex, boots.item());
        return Optional.of(List.copyOf(nonBootItems));
    }

    private void accumulateBuildObservations(
            ChampionBuildStats stats,
            Map<Long, ItemSummary> bootsByItem,
            Map<Long, Map<Integer, ItemSummary>> bootPositionsByItem,
            Map<Integer, Map<Long, ItemSummary>> lateItemsByOrder
    ) {
        int games = countOrZero(stats.getGameCount());
        int wins = countOrZero(stats.getWinCount());
        int nonBootOrder = 0;

        List<Item> items = stats.getItems();
        for (int position = 0; position < items.size(); position++) {
            Item item = items.get(position);
            if (item.hasTag(BOOTS_TAG)) {
                mergeSummary(bootsByItem, item.getItemId(), item, position, games, wins);
                Map<Integer, ItemSummary> positions = bootPositionsByItem.computeIfAbsent(
                        item.getItemId(),
                        ignored -> new LinkedHashMap<>()
                );
                mergeSummary(positions, position, item, position, games, wins);
                continue;
            }

            if (nonBootOrder >= CORE_ITEM_COUNT) {
                int lateOrder = nonBootOrder - CORE_ITEM_COUNT;
                Map<Long, ItemSummary> candidates = lateItemsByOrder.computeIfAbsent(
                        lateOrder,
                        ignored -> new LinkedHashMap<>()
                );
                mergeSummary(candidates, item.getItemId(), item, lateOrder, games, wins);
            }
            nonBootOrder++;
        }
    }

    private <K> void mergeSummary(
            Map<K, ItemSummary> summaries,
            K key,
            Item item,
            int position,
            int games,
            int wins
    ) {
        summaries.merge(
                key,
                new ItemSummary(item, position, games, wins),
                ItemSummary::combine
        );
    }

    private ItemSummary selectBest(
            Map<?, ItemSummary> candidates,
            Set<Long> excludedItemIds
    ) {
        ItemSummary selected = null;
        for (ItemSummary candidate : candidates.values()) {
            if (excludedItemIds.contains(candidate.item().getItemId())) {
                continue;
            }
            if (selected == null || isBetter(candidate, selected)) {
                selected = candidate;
            }
        }
        return selected;
    }

    private boolean hasExactlyOneBoots(List<Item> items) {
        return items.stream()
                .filter(item -> item.hasTag(BOOTS_TAG))
                .count() == 1;
    }

    private static boolean isBetter(ItemSummary candidate, ItemSummary current) {
        return candidate.gameCount() > current.gameCount()
                || candidate.gameCount() == current.gameCount()
                && candidate.winCount() > current.winCount();
    }

    private static boolean isBetter(
            PurchaseOrderSummary candidate,
            PurchaseOrderSummary current
    ) {
        return candidate.gameCount() > current.gameCount()
                || candidate.gameCount() == current.gameCount()
                && candidate.winCount() > current.winCount();
    }

    private static boolean isBetter(
            ChampionBuildStats candidate,
            ChampionBuildStats current
    ) {
        int candidateGames = countOrZero(candidate.getGameCount());
        int currentGames = countOrZero(current.getGameCount());
        int candidateWins = countOrZero(candidate.getWinCount());
        int currentWins = countOrZero(current.getWinCount());

        return candidateGames > currentGames
                || candidateGames == currentGames
                && candidateWins > currentWins;
    }

    private static List<Long> orderedCoreKey(CoreBuildSequence sequence) {
        return sequence.getOrderedItems()
                .stream()
                .map(item -> item.getItemId())
                .toList();
    }

    private static int countOrZero(Integer count) {
        return count == null ? 0 : Math.max(count, 0);
    }

    public List<Long> getClusterKey() {
        return clusterKey;
    }

    public List<ChampionBuildStats> getObservedStats() {
        return observedStats;
    }

    public int getGameCount() {
        return gameCount;
    }

    public int getWinCount() {
        return winCount;
    }

    public CoreBuildSequence getRepresentativeSequence() {
        return representativeSequence;
    }

    private record PurchaseOrderSummary(
            CoreBuildSequence sequence,
            int gameCount,
            int winCount
    ) {
        private PurchaseOrderSummary combine(PurchaseOrderSummary other) {
            return new PurchaseOrderSummary(
                    sequence,
                    gameCount + other.gameCount,
                    winCount + other.winCount
            );
        }
    }

    private record ItemSummary(
            Item item,
            int position,
            int gameCount,
            int winCount
    ) {
        private ItemSummary combine(ItemSummary other) {
            return new ItemSummary(
                    item,
                    position,
                    gameCount + other.gameCount,
                    winCount + other.winCount
            );
        }
    }
}
