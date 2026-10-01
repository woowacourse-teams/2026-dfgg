package dfgg.application.itemstats;

import java.util.Set;

public record ItemStatsAggregationResult(
        Set<String> recentPatches,
        long championItemStatsCount,
        long championItemRollupCount,
        long championPairItemStatsCount,
        long itemMetaStatsCount,
        long durationMillis
) {
}
