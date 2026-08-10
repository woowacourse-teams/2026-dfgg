package dfgg.application;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChampionBuildStatsRebuildService {

    private static final Logger log = LoggerFactory.getLogger(ChampionBuildStatsRebuildService.class);
    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";
    private static final String AGGREGATION_REVISION = "v1";

    private final RawMatchRepository rawMatchRepository;
    private final RawMatchTimelineRepository rawMatchTimelineRepository;
    private final ItemRepository itemRepository;
    private final ChampionBuildStatsMatchProcessor matchProcessor;
    private final StatsAggregationCompletionRepository completionRepository;
    private final MatchParticipantCohortRepository cohortRepository;
    private final int batchSize;

    public ChampionBuildStatsRebuildService(
            RawMatchRepository rawMatchRepository,
            RawMatchTimelineRepository rawMatchTimelineRepository,
            ItemRepository itemRepository,
            ChampionBuildStatsMatchProcessor matchProcessor,
            StatsAggregationCompletionRepository completionRepository,
            MatchParticipantCohortRepository cohortRepository,
            @Value("${stats.rebuild.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("stats rebuild batch size must be positive");
        }
        this.rawMatchRepository = rawMatchRepository;
        this.rawMatchTimelineRepository = rawMatchTimelineRepository;
        this.itemRepository = itemRepository;
        this.matchProcessor = matchProcessor;
        this.completionRepository = completionRepository;
        this.cohortRepository = cohortRepository;
        this.batchSize = batchSize;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChampionBuildStatsRebuildResult rebuildAll(String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        return rebuildPendingMatches(tier);
    }

    private ChampionBuildStatsRebuildResult rebuildPendingMatches(String tier) {
        long startedAtNanos = System.nanoTime();
        log.info("Champion build stats rebuild requested: tier={}", tier);
        int totalMatches = Math.toIntExact(completionRepository.countPendingMatches(
                QUEUE_TYPE,
                tier,
                AGGREGATION_REVISION
        ));
        log.info("Champion build stats rebuild started: tier={}, totalMatches={}", tier, totalMatches);
        if (totalMatches == 0) {
            ChampionBuildStatsRebuildResult result = new ChampionBuildStatsRebuildResult(0, 0, 0, 0);
            logCompleted(tier, result, startedAtNanos);
            return result;
        }

        Set<Integer> coreItemIds = coreItemIds();
        int processedMatches = 0;
        int recordedSamples = 0;
        int skippedMatches = 0;
        List<ChampionBuildStatsRebuildResult.Failure> failures = new ArrayList<>();
        int visitedMatches = 0;
        int progressLogInterval = progressLogInterval(totalMatches);
        PendingTarget cursor = PendingTarget.initialCursor();

        while (true) {
            List<PendingTarget> targets = nextPendingTargets(tier, cursor);
            if (targets.isEmpty()) {
                break;
            }
            for (Map.Entry<String, List<String>> entry : groupPuuidsByMatch(targets).entrySet()) {
                visitedMatches++;
                String matchId = entry.getKey();
                RawMatch rawMatch = rawMatchRepository.findById(matchId).orElse(null);
                if (rawMatch == null) {
                    failures.add(new ChampionBuildStatsRebuildResult.Failure(
                            matchId,
                            "IllegalStateException: raw match not found"
                    ));
                    continue;
                }
                RawMatchTimeline timeline = rawMatchTimelineRepository.findById(matchId).orElse(null);
                if (timeline == null) {
                    skippedMatches++;
                    log.warn("Skipping match {} because its raw timeline is missing", matchId);
                    logProgress(
                            tier,
                            visitedMatches,
                            totalMatches,
                            processedMatches,
                            skippedMatches,
                            failures.size(),
                            recordedSamples,
                            progressLogInterval
                    );
                    continue;
                }
                try {
                    ChampionBuildStatsMatchProcessor.Result matchResult = matchProcessor.rebuild(
                            rawMatch,
                            timeline,
                            QUEUE_TYPE,
                            tier,
                            entry.getValue(),
                            coreItemIds,
                            AGGREGATION_REVISION
                    );
                    if (matchResult.claimedParticipants() > 0) {
                        recordedSamples += matchResult.recordedSamples();
                        processedMatches++;
                    }
                } catch (RuntimeException exception) {
                    failures.add(new ChampionBuildStatsRebuildResult.Failure(
                            matchId,
                            failureReason(exception)
                    ));
                    log.error(
                            "Champion build stats match failed and will be skipped: tier={}, matchId={}, "
                                    + "visitedMatches={}/{}, processedMatches={}, skippedMissingTimeline={}, "
                                    + "failedMatches={}, recordedSamples={}",
                            tier,
                            matchId,
                            visitedMatches,
                            totalMatches,
                            processedMatches,
                            skippedMatches,
                            failures.size(),
                            recordedSamples,
                            exception
                    );
                }
                logProgress(
                        tier,
                        visitedMatches,
                        totalMatches,
                        processedMatches,
                        skippedMatches,
                        failures.size(),
                        recordedSamples,
                        progressLogInterval
                );
            }
            cursor = targets.getLast();
        }
        logSkippedMatches(skippedMatches);
        ChampionBuildStatsRebuildResult result = new ChampionBuildStatsRebuildResult(
                totalMatches,
                processedMatches,
                skippedMatches,
                failures.size(),
                recordedSamples,
                failures
        );
        logCompleted(tier, result, startedAtNanos);
        return result;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChampionBuildStatsRebuildResult replayOne(String matchId, String tier) {
        Objects.requireNonNull(matchId, "matchId must not be null");
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        List<String> cohortPuuids = cohortRepository.findPuuidsByMatchIdAndQueueTypeAndTier(
                matchId,
                QUEUE_TYPE,
                tier
        );
        if (cohortPuuids.isEmpty()) {
            throw new IllegalArgumentException("stats cohort not found: " + matchId + "/" + tier);
        }
        Set<Integer> coreItemIds = coreItemIds();
        RawMatch rawMatch = rawMatchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("raw match not found: " + matchId));
        RawMatchTimeline timeline = rawMatchTimelineRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("raw match timeline not found: " + matchId));
        ChampionBuildStatsMatchProcessor.ReplayResult replayResult = matchProcessor.replay(
                rawMatch,
                timeline,
                QUEUE_TYPE,
                tier,
                cohortPuuids,
                coreItemIds,
                AGGREGATION_REVISION
        );
        return new ChampionBuildStatsRebuildResult(
                1,
                replayResult.replayedParticipants() > 0 ? 1 : 0,
                0,
                replayResult.recordedSamples()
        );
    }

    private List<PendingTarget> nextPendingTargets(String tier, PendingTarget cursor) {
        List<PendingTarget> targets = completionRepository.findPendingTargetsAfter(
                        QUEUE_TYPE,
                        tier,
                        AGGREGATION_REVISION,
                        cursor.matchId(),
                        cursor.puuid(),
                        batchSize
                ).stream()
                .map(PendingTarget::from)
                .collect(Collectors.toCollection(ArrayList::new));
        if (targets.isEmpty()) {
            return List.of();
        }

        PendingTarget lastTarget = targets.getLast();
        completionRepository.findRemainingTargetsForMatch(
                        lastTarget.matchId(),
                        QUEUE_TYPE,
                        tier,
                        AGGREGATION_REVISION,
                        lastTarget.puuid()
                ).stream()
                .map(PendingTarget::from)
                .forEach(targets::add);
        return List.copyOf(targets);
    }

    private Map<String, List<String>> groupPuuidsByMatch(List<PendingTarget> targets) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (PendingTarget target : targets) {
            grouped.computeIfAbsent(target.matchId(), ignored -> new ArrayList<>())
                    .add(target.puuid());
        }
        return grouped;
    }

    private Set<Integer> coreItemIds() {
        return itemRepository.findAll().stream()
                .map(Item::getItemId)
                .map(Math::toIntExact)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void logSkippedMatches(int skippedMatches) {
        if (skippedMatches > 0) {
            log.warn("Skipped {} matches during ChampionBuildStats rebuild because timelines are missing", skippedMatches);
        }
    }

    private int progressLogInterval(int totalMatches) {
        return Math.max(1, (totalMatches + 9) / 10);
    }

    private void logProgress(
            String tier,
            int visitedMatches,
            int totalMatches,
            int processedMatches,
            int skippedMatches,
            int failedMatches,
            int recordedSamples,
            int progressLogInterval
    ) {
        if (visitedMatches % progressLogInterval != 0 && visitedMatches != totalMatches) {
            return;
        }
        log.info(
                "Champion build stats rebuild progress: tier={}, visitedMatches={}/{}, processedMatches={}, "
                        + "skippedMissingTimeline={}, failedMatches={}, recordedSamples={}",
                tier,
                visitedMatches,
                totalMatches,
                processedMatches,
                skippedMatches,
                failedMatches,
                recordedSamples
        );
    }

    private void logCompleted(
            String tier,
            ChampionBuildStatsRebuildResult result,
            long startedAtNanos
    ) {
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        log.info(
                "Champion build stats rebuild completed: tier={}, totalMatches={}, processedMatches={}, "
                        + "skippedMissingTimeline={}, failedMatches={}, recordedSamples={}, durationMs={}",
                tier,
                result.totalMatches(),
                result.processedMatches(),
                result.skippedMissingTimeline(),
                result.failedMatches(),
                result.recordedSamples(),
                durationMillis
        );
    }

    private String failureReason(RuntimeException exception) {
        String type = exception.getClass().getSimpleName();
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return type;
        }
        return type + ": " + exception.getMessage();
    }

    private record PendingTarget(String matchId, String puuid) {

        private static PendingTarget initialCursor() {
            return new PendingTarget("", "");
        }

        private static PendingTarget from(StatsAggregationCompletionRepository.PendingTarget target) {
            return new PendingTarget(target.getMatchId(), target.getPuuid());
        }
    }
}
