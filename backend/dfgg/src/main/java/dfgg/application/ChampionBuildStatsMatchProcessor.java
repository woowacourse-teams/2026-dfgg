package dfgg.application;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchTimeline;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChampionBuildStatsMatchProcessor {

    private final MatchNormalizer matchNormalizer;
    private final NormalizedMatchPersistenceService normalizedPersistenceService;
    private final ChampionBuildStatsAggregationService aggregationService;

    public ChampionBuildStatsMatchProcessor(
            MatchNormalizer matchNormalizer,
            NormalizedMatchPersistenceService normalizedPersistenceService,
            ChampionBuildStatsAggregationService aggregationService
    ) {
        this.matchNormalizer = matchNormalizer;
        this.normalizedPersistenceService = normalizedPersistenceService;
        this.aggregationService = aggregationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int rebuild(
            RawMatch rawMatch,
            RawMatchTimeline timeline,
            String tier,
            Collection<String> cohortPuuids,
            Set<Integer> coreItemIds
    ) {
        Objects.requireNonNull(rawMatch, "rawMatch must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        Objects.requireNonNull(cohortPuuids, "cohortPuuids must not be null");
        Objects.requireNonNull(coreItemIds, "coreItemIds must not be null");

        var normalized = matchNormalizer.normalize(
                rawMatch.getMatchId(),
                rawMatch.getRawData(),
                timeline.getRawData(),
                coreItemIds
        );
        normalizedPersistenceService.replace(normalized);
        return aggregationService.aggregate(normalized, tier, cohortPuuids);
    }
}
