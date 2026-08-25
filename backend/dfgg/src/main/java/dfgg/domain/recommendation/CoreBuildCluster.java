package dfgg.domain.recommendation;

import dfgg.domain.stats.ChampionBuildStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CoreBuildCluster {

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
}
