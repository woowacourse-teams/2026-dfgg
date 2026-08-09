package dfgg.application;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChampionBuildStatsRebuildService {

    private static final Logger log = LoggerFactory.getLogger(ChampionBuildStatsRebuildService.class);

    private final RawMatchRepository rawMatchRepository;
    private final RawMatchTimelineRepository rawMatchTimelineRepository;
    private final ItemRepository itemRepository;
    private final ChampionBuildStatsMatchProcessor matchProcessor;
    private final MatchParticipantCohortRepository cohortRepository;

    public ChampionBuildStatsRebuildService(
            RawMatchRepository rawMatchRepository,
            RawMatchTimelineRepository rawMatchTimelineRepository,
            ItemRepository itemRepository,
            ChampionBuildStatsMatchProcessor matchProcessor,
            MatchParticipantCohortRepository cohortRepository
    ) {
        this.rawMatchRepository = rawMatchRepository;
        this.rawMatchTimelineRepository = rawMatchTimelineRepository;
        this.itemRepository = itemRepository;
        this.matchProcessor = matchProcessor;
        this.cohortRepository = cohortRepository;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChampionBuildStatsRebuildResult rebuildAll(String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        return rebuildAllMatches(
                tier,
                rawMatch -> cohortRepository.findPuuidsByMatchIdAndQueueTypeAndTier(
                        rawMatch.getMatchId(),
                        "RANKED_SOLO_5x5",
                        tier
                )
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChampionBuildStatsRebuildResult rebuildAll(String tier, Collection<String> cohortPuuids) {
        Objects.requireNonNull(cohortPuuids, "cohortPuuids must not be null");
        return rebuildAllMatches(tier, rawMatch -> cohortPuuids);
    }

    private ChampionBuildStatsRebuildResult rebuildAllMatches(
            String tier,
            Function<RawMatch, Collection<String>> cohortPuuidsResolver
    ) {
        long startedAtNanos = System.nanoTime();
        log.info("Champion build stats rebuild requested: tier={}", tier);
        Set<Integer> coreItemIds = coreItemIds();
        List<RawMatch> rawMatches = rawMatchRepository.findAll();
        int totalMatches = rawMatches.size();
        int processedMatches = 0;
        int recordedSamples = 0;
        int skippedMatches = 0;
        List<ChampionBuildStatsRebuildResult.Failure> failures = new ArrayList<>();
        int visitedMatches = 0;
        int progressLogInterval = progressLogInterval(totalMatches);
        log.info("Champion build stats rebuild started: tier={}, totalMatches={}", tier, totalMatches);

        for (RawMatch rawMatch : rawMatches) {
            visitedMatches++;
            RawMatchTimeline timeline = rawMatchTimelineRepository.findById(rawMatch.getMatchId()).orElse(null);
            if (timeline == null) {
                skippedMatches++;
                log.warn("Skipping match {} because its raw timeline is missing", rawMatch.getMatchId());
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
            Collection<String> cohortPuuids = cohortPuuidsResolver.apply(rawMatch);
            try {
                recordedSamples += matchProcessor.rebuild(
                        rawMatch,
                        timeline,
                        tier,
                        cohortPuuids,
                        coreItemIds
                );
                processedMatches++;
            } catch (RuntimeException exception) {
                failures.add(new ChampionBuildStatsRebuildResult.Failure(
                        rawMatch.getMatchId(),
                        failureReason(exception)
                ));
                log.error(
                        "Champion build stats match failed and will be skipped: tier={}, matchId={}, "
                                + "visitedMatches={}/{}, processedMatches={}, skippedMissingTimeline={}, "
                                + "failedMatches={}, recordedSamples={}",
                        tier,
                        rawMatch.getMatchId(),
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
    public int rebuildOne(String matchId, String tier, Collection<String> cohortPuuids) {
        Objects.requireNonNull(matchId, "matchId must not be null");
        Set<Integer> coreItemIds = coreItemIds();
        RawMatch rawMatch = rawMatchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("raw match not found: " + matchId));
        RawMatchTimeline timeline = rawMatchTimelineRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("raw match timeline not found: " + matchId));
        return matchProcessor.rebuild(rawMatch, timeline, tier, cohortPuuids, coreItemIds);
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
}
