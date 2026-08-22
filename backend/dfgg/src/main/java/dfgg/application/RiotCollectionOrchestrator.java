package dfgg.application;

import dfgg.application.match.RiotMatchSyncService;
import dfgg.application.player.RiotPlayerSyncService;
import dfgg.infrastructure.config.RiotSchedulerProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiotCollectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RiotCollectionOrchestrator.class);
    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";
    private static final List<String> DIVISION_ORDER = List.of("IV", "III", "II", "I");

    private final RiotSchedulerProperties properties;
    private final RiotPlayerSyncService playerSyncService;
    private final RiotMatchSyncService matchSyncService;
    private final ChampionBuildStatsRebuildService statsRebuildService;
    private int nextLeaguePage;
    private int nextDivisionIndex;
    private boolean currentLeagueRangeHasPlayers;

    public RiotCollectionOrchestrator(
            RiotSchedulerProperties properties,
            RiotPlayerSyncService playerSyncService,
            RiotMatchSyncService matchSyncService,
            ChampionBuildStatsRebuildService statsRebuildService
    ) {
        this.properties = properties;
        this.playerSyncService = playerSyncService;
        this.matchSyncService = matchSyncService;
        this.statsRebuildService = statsRebuildService;
        this.nextLeaguePage = 1;
        this.nextDivisionIndex = 0;
        this.currentLeagueRangeHasPlayers = false;
    }

    public void runOnce() {
        Instant startedAt = Instant.now();
        RunSummary summary = new RunSummary();
        try {
            validateProperties();
        } catch (RuntimeException exception) {
            summary.failures.add(Failure.from("CONFIG", "collection.scheduler", exception));
            logFailures(summary.failures);
            logCompleted("FAILED", startedAt, Instant.now(), summary);
            return;
        }

        log.info(
                "Riot collection scheduler started: startedAt={}, queue={}, tiers={}, division={}, "
                        + "leaguePageStart={}",
                startedAt,
                QUEUE_TYPE,
                properties.getTiers(),
                currentDivision(),
                nextLeaguePage
        );
        List<String> collectedPuuids = collectPlayers(summary);
        collectMatches(collectedPuuids, summary);
        collectMissingTimelines(summary);
        rebuildStats(summary);

        Instant finishedAt = Instant.now();
        logFailures(summary.failures);
        logCompleted(
                summary.failures.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_FAILURES",
                startedAt,
                finishedAt,
                summary
        );
    }

    private List<String> collectPlayers(RunSummary summary) {
        boolean completed = true;
        LinkedHashSet<String> collectedPuuids = new LinkedHashSet<>();
        String division = currentDivision();
        for (String tier : properties.getTiers()) {
            int pageEnd = nextLeaguePage + properties.getLeaguePageCount();
            for (int page = nextLeaguePage; page < pageEnd; page++) {
                String target = tier + "/" + division + "/page-" + page;
                try {
                    RiotPlayerSyncService.SyncResult syncResult = playerSyncService.syncLeagueEntries(
                            QUEUE_TYPE, tier, division, page
                    );
                    summary.newPlayers += syncResult.newPlayers();
                    collectedPuuids.addAll(syncResult.puuids());
                } catch (RuntimeException exception) {
                    completed = false;
                    summary.failures.add(Failure.from("PLAYERS", target, exception));
                }
            }
        }
        currentLeagueRangeHasPlayers |= !collectedPuuids.isEmpty();
        if (completed) {
            moveToNextLeagueRange();
        }
        return List.copyOf(collectedPuuids);
    }

    private void moveToNextLeagueRange() {
        nextDivisionIndex++;
        if (nextDivisionIndex >= progressiveDivisions().size()) {
            nextDivisionIndex = 0;
            if (currentLeagueRangeHasPlayers) {
                nextLeaguePage += properties.getLeaguePageCount();
            } else {
                nextLeaguePage = 1;
            }
            currentLeagueRangeHasPlayers = false;
        }
    }

    private String currentDivision() {
        List<String> divisions = progressiveDivisions();
        return divisions.get(Math.min(nextDivisionIndex, divisions.size() - 1));
    }

    private List<String> progressiveDivisions() {
        List<String> configured = properties.getDivisions();
        if (configured.size() != 1) {
            return configured;
        }
        int startIndex = DIVISION_ORDER.indexOf(configured.getFirst());
        if (startIndex < 0) {
            return configured;
        }
        return DIVISION_ORDER.subList(startIndex, DIVISION_ORDER.size());
    }

    private void collectMatches(List<String> puuids, RunSummary summary) {
        int playerCount = properties.getPlayerPageSize();
        for (int fromIndex = 0; fromIndex < puuids.size(); fromIndex += playerCount) {
            List<String> targets = puuids.subList(
                    fromIndex,
                    Math.min(fromIndex + playerCount, puuids.size())
            );
            try {
                merge(summary, matchSyncService.syncMatches(
                        targets,
                        0,
                        properties.getMatchCount()
                ));
            } catch (RuntimeException exception) {
                summary.failures.add(Failure.from(
                        "MATCH_BATCH",
                        Integer.toString(fromIndex / playerCount),
                        exception
                ));
            }
        }
    }

    private void collectMissingTimelines(RunSummary summary) {
        try {
            matchSyncService.syncMissingTimelines();
        } catch (RuntimeException exception) {
            summary.failures.add(Failure.from("TIMELINE_PAGE", "all-missing", exception));
        }
    }

    private void rebuildStats(RunSummary summary) {
        for (String tier : properties.getTiers()) {
            try {
                ChampionBuildStatsRebuildResult rebuildResult = statsRebuildService.rebuildAll(tier);
                summary.processedMatches += rebuildResult.processedMatches();
                summary.recordedSamples += rebuildResult.recordedSamples();
                summary.skippedItems += rebuildResult.skippedMissingTimeline();
                rebuildResult.failures().forEach(failure -> summary.failures.add(new Failure(
                        "STATS",
                        failure.matchId(),
                        failure.reason()
                )));
            } catch (RuntimeException exception) {
                summary.failures.add(Failure.from("STATS", tier, exception));
            }
        }
    }

    private void merge(RunSummary summary, RiotMatchSyncService.SyncResult syncResult) {
        summary.newMatches += syncResult.newMatches();
        summary.newTimelines += syncResult.newTimelines();
        summary.skippedItems += syncResult.skippedItems();
        syncResult.failures().forEach(failure -> summary.failures.add(new Failure(
                failure.stage(),
                failure.targetId(),
                failure.reason()
        )));
    }

    private void validateProperties() {
        if (properties.getTiers().isEmpty()) {
            throw new IllegalArgumentException("collection scheduler tiers must not be empty");
        }
        if (properties.getDivisions().isEmpty()) {
            throw new IllegalArgumentException("collection scheduler divisions must not be empty");
        }
        if (properties.getDivisions().stream().anyMatch(division -> !DIVISION_ORDER.contains(division))) {
            throw new IllegalArgumentException("collection scheduler divisions must be one of IV, III, II, I");
        }
        if (properties.getLeaguePageCount() < 1) {
            throw new IllegalArgumentException("collection scheduler league page count must be positive");
        }
        if (properties.getPlayerPageSize() < 1 || properties.getPlayerPageSize() > 100) {
            throw new IllegalArgumentException("collection scheduler player page size must be between 1 and 100");
        }
        if (properties.getMatchCount() < 1 || properties.getMatchCount() > 100) {
            throw new IllegalArgumentException("collection scheduler match count must be between 1 and 100");
        }
    }

    private void logFailures(List<Failure> failures) {
        failures.forEach(failure -> log.warn(
                "Riot collection item failed: stage={}, targetId={}, reason={}",
                failure.stage,
                failure.targetId,
                failure.reason
        ));
    }

    private void logCompleted(String status, Instant startedAt, Instant finishedAt, RunSummary summary) {
        log.info(
                "Riot collection scheduler completed: status={}, startedAt={}, finishedAt={}, durationMs={}, "
                        + "newPlayers={}, newMatches={}, newTimelines={}, processedMatches={}, "
                        + "recordedSamples={}, skippedItems={}, failureCounts={}",
                status,
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt).toMillis(),
                summary.newPlayers,
                summary.newMatches,
                summary.newTimelines,
                summary.processedMatches,
                summary.recordedSamples,
                summary.skippedItems,
                failureCounts(summary.failures)
        );
    }

    private Map<String, Long> failureCounts(List<Failure> failures) {
        Map<String, Long> counts = new LinkedHashMap<>();
        failures.forEach(failure -> counts.merge(failure.stage, 1L, Long::sum));
        return Map.copyOf(counts);
    }

    private static class RunSummary {
        private int newPlayers;
        private int newMatches;
        private int newTimelines;
        private int processedMatches;
        private int recordedSamples;
        private int skippedItems;
        private final List<Failure> failures = new ArrayList<>();
    }

    private record Failure(String stage, String targetId, String reason) {

        private static Failure from(String stage, String targetId, RuntimeException exception) {
            String type = exception.getClass().getSimpleName();
            String message = exception.getMessage();
            return new Failure(
                    stage,
                    targetId,
                    message == null || message.isBlank() ? type : type + ": " + message
            );
        }
    }
}
