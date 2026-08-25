package dfgg.domain.recommendation;

import dfgg.domain.stats.ChampionBuildStats;
import java.util.List;

public final class CoreBuildCluster {

    private final List<Long> clusterKey;
    private final List<ChampionBuildStats> observedStats;

    private CoreBuildCluster(List<Long> clusterKey, List<ChampionBuildStats> obserbedStats) {
        this.clusterKey = List.copyOf(clusterKey);
        this.observedStats = List.copyOf(obserbedStats);
    }

    public static CoreBuildCluster from(List<Long> clusterKey, List<ChampionBuildStats> observedStats) {
        if (observedStats.isEmpty()) {
            throw new IllegalArgumentException("관측 빌드 통계는 비어 있을 수 없습니다.");
        }

        for (ChampionBuildStats stats : observedStats) {
            CoreBuildSequence sequence = CoreBuildSequence.from(stats.getItems())
                    .orElseThrow(() -> new IllegalArgumentException("관측 빌드에는 신발을 제외한 코어 아이템 3개가 필요합니다."));
            if (!clusterKey.equals(sequence.clusterKey())) {
                throw new IllegalArgumentException("모든 관측 빌드는 동일한 clusterKey를 가져야 합니다.");
            }
        }

        return new CoreBuildCluster(clusterKey, observedStats);
    }

    public List<Long> getClusterKey() {
        return clusterKey;
    }

    public List<ChampionBuildStats> getObservedStats() {
        return observedStats;
    }
}
