package dfgg.application;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private final StatsAggregationCompletionRepository completionRepository;

    public ChampionBuildStatsMatchProcessor(
            MatchNormalizer matchNormalizer,
            NormalizedMatchPersistenceService normalizedPersistenceService,
            ChampionBuildStatsAggregationService aggregationService,
            StatsAggregationCompletionRepository completionRepository
    ) {
        this.matchNormalizer = matchNormalizer;
        this.normalizedPersistenceService = normalizedPersistenceService;
        this.aggregationService = aggregationService;
        this.completionRepository = completionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result rebuild(
            RawMatch rawMatch,
            RawMatchTimeline timeline,
            String queueType,
            String tier,
            Collection<String> cohortPuuids,
            Set<Integer> coreItemIds,
            String aggregationRevision
    ) {
        Objects.requireNonNull(rawMatch, "rawMatch must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        if (queueType == null || queueType.isBlank()) {
            throw new IllegalArgumentException("queueType must not be blank");
        }
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        Objects.requireNonNull(cohortPuuids, "cohortPuuids must not be null");
        Objects.requireNonNull(coreItemIds, "coreItemIds must not be null");
        if (aggregationRevision == null || aggregationRevision.isBlank()) {
            throw new IllegalArgumentException("aggregationRevision must not be blank");
        }

        completionRepository.acquireMatchLock(rawMatch.getMatchId());
        List<String> claimedPuuids = claimPendingPuuids(
                rawMatch.getMatchId(),
                cohortPuuids,
                queueType,
                tier,
                aggregationRevision
        );
        if (claimedPuuids.isEmpty()) {
            return new Result(0, 0);
        }

        var normalized = matchNormalizer.normalize(
                rawMatch.getMatchId(),
                rawMatch.getRawData(),
                timeline.getRawData(),
                coreItemIds
        );
        normalizedPersistenceService.replace(normalized);
        int recordedSamples = aggregationService.aggregate(normalized, tier, claimedPuuids);
        return new Result(claimedPuuids.size(), recordedSamples);
    }

    private List<String> claimPendingPuuids(
            String matchId,
            Collection<String> cohortPuuids,
            String queueType,
            String tier,
            String aggregationRevision
    ) {
        List<String> claimedPuuids = new ArrayList<>();
        for (String puuid : cohortPuuids.stream().distinct().sorted().toList()) {
            int inserted = completionRepository.insertIfAbsent(
                    matchId,
                    puuid,
                    queueType,
                    tier,
                    aggregationRevision
            );
            if (inserted == 1) {
                claimedPuuids.add(puuid);
            }
        }
        return List.copyOf(claimedPuuids);
    }

    public record Result(int claimedParticipants, int recordedSamples) {
    }
}
