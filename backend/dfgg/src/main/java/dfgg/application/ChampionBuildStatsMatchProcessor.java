package dfgg.application;

import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.stats.CompositionStatsSampleRepository;
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
    private final CompositionStatsSampleRepository sampleRepository;

    public ChampionBuildStatsMatchProcessor(
            MatchNormalizer matchNormalizer,
            NormalizedMatchPersistenceService normalizedPersistenceService,
            ChampionBuildStatsAggregationService aggregationService,
            StatsAggregationCompletionRepository completionRepository,
            CompositionStatsSampleRepository sampleRepository
    ) {
        this.matchNormalizer = matchNormalizer;
        this.normalizedPersistenceService = normalizedPersistenceService;
        this.aggregationService = aggregationService;
        this.completionRepository = completionRepository;
        this.sampleRepository = sampleRepository;
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
        validateScope(queueType, tier, cohortPuuids, coreItemIds, aggregationRevision);

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReplayResult replay(
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
        validateScope(queueType, tier, cohortPuuids, coreItemIds, aggregationRevision);

        List<String> targetPuuids = cohortPuuids.stream().distinct().sorted().toList();
        if (targetPuuids.isEmpty()) {
            throw new IllegalArgumentException("cohortPuuids must not be empty");
        }
        completionRepository.acquireMatchLock(rawMatch.getMatchId());
        NormalizedMatch normalized = matchNormalizer.normalize(
                rawMatch.getMatchId(),
                rawMatch.getRawData(),
                timeline.getRawData(),
                coreItemIds
        );
        ensureParticipantsExist(normalized, targetPuuids);

        for (String puuid : targetPuuids) {
            removePreviousContribution(rawMatch.getMatchId(), puuid);
        }
        normalizedPersistenceService.replace(normalized);
        int recordedSamples = aggregationService.aggregate(normalized, tier, targetPuuids);
        for (String puuid : targetPuuids) {
            completionRepository.markCompleted(
                    rawMatch.getMatchId(),
                    puuid,
                    queueType,
                    tier,
                    aggregationRevision
            );
        }
        return new ReplayResult(targetPuuids.size(), recordedSamples);
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

    private void validateScope(
            String queueType,
            String tier,
            Collection<String> cohortPuuids,
            Set<Integer> coreItemIds,
            String aggregationRevision
    ) {
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
    }

    private void ensureParticipantsExist(NormalizedMatch normalized, List<String> targetPuuids) {
        Set<String> normalizedPuuids = normalized.participants().stream()
                .map(NormalizedParticipant::puuid)
                .collect(java.util.stream.Collectors.toSet());
        List<String> missingPuuids = targetPuuids.stream()
                .filter(puuid -> !normalizedPuuids.contains(puuid))
                .toList();
        if (!missingPuuids.isEmpty()) {
            throw new IllegalStateException("cohort participants not found in normalized match: " + missingPuuids);
        }
    }

    private void removePreviousContribution(String matchId, String puuid) {
        sampleRepository.backfillMissingWinFromNormalized(matchId, puuid);
        long missingWinCount = sampleRepository.countByMatchIdAndPuuidAndWinIsNull(matchId, puuid);
        if (missingWinCount > 0) {
            throw new IllegalStateException(
                    "cannot replay stats because previous win contributions are unknown: "
                            + matchId + "/" + puuid + " (" + missingWinCount + ")"
            );
        }

        long contributionCount = sampleRepository.countByMatchIdAndPuuid(matchId, puuid);
        int decrementedStats = sampleRepository.deleteContributionsAndDecrement(matchId, puuid);
        if (decrementedStats != contributionCount) {
            throw new IllegalStateException(
                    "cannot replay stats because stored counts do not match samples: "
                            + matchId + "/" + puuid
            );
        }
    }

    public record Result(int claimedParticipants, int recordedSamples) {
    }

    public record ReplayResult(int replayedParticipants, int recordedSamples) {
    }
}
