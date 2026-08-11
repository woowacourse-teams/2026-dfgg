package dfgg.application;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.player.PlayerRepository;
import dfgg.domain.player.PlayerCohortRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class RiotMatchSyncService {

    private static final Logger log = LoggerFactory.getLogger(RiotMatchSyncService.class);
    private static final String PLATFORM = "KR";
    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";
    private static final int TIMELINE_BATCH_SIZE = 100;

    private final RiotClient riotClient;
    private final PlayerRepository playerRepository;
    private final RawMatchRepository rawMatchRepository;
    private final RawMatchPersistenceService persistenceService;
    private final RawMatchTimelineRepository rawMatchTimelineRepository;
    private final RawMatchTimelinePersistenceService timelinePersistenceService;
    private final PlayerCohortRepository playerCohortRepository;
    private final MatchParticipantCohortPersistenceService cohortPersistenceService;

    public RiotMatchSyncService(
            RiotClient riotClient,
            PlayerRepository playerRepository,
            RawMatchRepository rawMatchRepository,
            RawMatchPersistenceService persistenceService,
            RawMatchTimelineRepository rawMatchTimelineRepository,
            RawMatchTimelinePersistenceService timelinePersistenceService,
            PlayerCohortRepository playerCohortRepository,
            MatchParticipantCohortPersistenceService cohortPersistenceService
    ) {
        this.riotClient = riotClient;
        this.playerRepository = playerRepository;
        this.rawMatchRepository = rawMatchRepository;
        this.persistenceService = persistenceService;
        this.rawMatchTimelineRepository = rawMatchTimelineRepository;
        this.timelinePersistenceService = timelinePersistenceService;
        this.playerCohortRepository = playerCohortRepository;
        this.cohortPersistenceService = cohortPersistenceService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SyncResult syncMatches(
            int playerPage,
            int playerCount,
            int matchStart,
            int matchCount
    ) {
        Assert.isTrue(playerPage >= 0, "playerPage must not be negative");
        Assert.isTrue(
                playerCount > 0 && playerCount <= 100,
                "playerCount must be between 1 and 100"
        );
        Assert.isTrue(matchStart >= 0, "start must not be negative");
        Assert.isTrue(
                matchCount > 0 && matchCount <= 100,
                "count must be between 1 and 100"
        );

        List<String> puuids = playerRepository.findPuuidsByPlatform(
                PLATFORM,
                PageRequest.of(playerPage, playerCount)
        );
        return syncMatches(puuids, matchStart, matchCount, "manual-page-" + playerPage);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SyncResult syncMatches(List<String> puuids, int matchCount) {
        Objects.requireNonNull(puuids, "puuids must not be null");
        puuids.forEach(puuid -> Assert.hasText(puuid, "puuid must not be blank"));
        Assert.isTrue(
                matchCount > 0 && matchCount <= 100,
                "count must be between 1 and 100"
        );
        List<String> distinctPuuids = new ArrayList<>(new LinkedHashSet<>(puuids));
        if (distinctPuuids.isEmpty()) {
            return SyncResult.empty(0, List.of());
        }
        return syncMatches(distinctPuuids, 0, matchCount, "scheduled");
    }

    private SyncResult syncMatches(
            List<String> puuids,
            int matchStart,
            int matchCount,
            String batch
    ) {
        long startedAtNanos = System.nanoTime();
        log.info(
                "Riot match sync started: batch={}, playerCount={}, matchCount={}",
                batch,
                puuids.size(),
                matchCount
        );
        List<Failure> failures = new ArrayList<>();
        MatchCollection matchCollection = collectMatchIds(puuids, matchStart, matchCount, failures);
        LinkedHashSet<String> matchIds = matchCollection.matchIds();
        log.info(
                "Riot match IDs collected: batch={}, scannedPlayers={}, uniqueMatchIds={}, failures={}",
                batch,
                matchCollection.scannedPlayers(),
                matchIds.size(),
                failures.size()
        );
        if (matchIds.isEmpty()) {
            SyncResult result = SyncResult.empty(matchCollection.scannedPlayers(), failures);
            logSyncCompleted(batch, result, startedAtNanos);
            return result;
        }

        Set<String> existingMatchIds = rawMatchRepository.findExistingMatchIds(matchIds);
        Set<String> existingTimelineIds = rawMatchTimelineRepository.findExistingMatchIds(matchIds);

        int newMatches = 0;
        int newTimelines = 0;
        int skippedItems = 0;
        int processedMatchIds = 0;
        int progressLogInterval = Math.max(1, (matchIds.size() + 9) / 10);
        for (String matchId : matchIds) {
            ItemCollectionResult result = fetchAndPersist(
                    matchId,
                    existingMatchIds.contains(matchId),
                    existingTimelineIds.contains(matchId),
                    matchCollection.cohortsByMatchId().getOrDefault(matchId, List.of()),
                    failures
            );
            newMatches += result.newMatches();
            newTimelines += result.newTimelines();
            skippedItems += result.skippedItems();
            processedMatchIds++;
            if (processedMatchIds % progressLogInterval == 0 || processedMatchIds == matchIds.size()) {
                log.info(
                        "Riot match sync progress: batch={}, processedMatchIds={}/{}, "
                                + "newMatches={}, newTimelines={}, skippedItems={}, failures={}",
                        batch,
                        processedMatchIds,
                        matchIds.size(),
                        newMatches,
                        newTimelines,
                        skippedItems,
                        failures.size()
                );
            }
        }
        SyncResult result = new SyncResult(
                matchCollection.scannedPlayers(),
                newMatches,
                newTimelines,
                skippedItems,
                failures
        );
        logSyncCompleted(batch, result, startedAtNanos);
        return result;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int syncMissingTimelines() {
        return syncMissingTimelinesWithResult().newTimelines();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SyncResult syncMissingTimelinesWithResult() {
        long startedAtNanos = System.nanoTime();
        log.info("Missing Riot timeline sync started: batchSize={}", TIMELINE_BATCH_SIZE);
        int persistedCount = 0;
        int skippedItems = 0;
        int scannedMatchIds = 0;
        int batchNumber = 0;
        List<Failure> failures = new ArrayList<>();
        String cursor = "";
        while (true) {
            List<String> matchIds = rawMatchRepository.findMatchIdsMissingTimelineAfter(
                    cursor,
                    PageRequest.of(0, TIMELINE_BATCH_SIZE)
            );
            if (matchIds.isEmpty()) {
                break;
            }
            batchNumber++;
            for (String matchId : matchIds) {
                try {
                    String rawData = riotClient.getRawMatchTimeline(matchId);
                    if (timelinePersistenceService.persist(new RawMatchTimeline(matchId, rawData))) {
                        persistedCount++;
                    } else {
                        skippedItems++;
                    }
                } catch (RuntimeException exception) {
                    recordFailure(failures, "TIMELINE", matchId, exception);
                }
            }
            scannedMatchIds += matchIds.size();
            cursor = matchIds.getLast();
            log.info(
                    "Missing Riot timeline sync progress: batch={}, scannedMatchIds={}, "
                            + "newTimelines={}, skippedItems={}, failures={}, cursor={}",
                    batchNumber,
                    scannedMatchIds,
                    persistedCount,
                    skippedItems,
                    failures.size(),
                    cursor
            );
        }
        SyncResult result = new SyncResult(0, 0, persistedCount, skippedItems, failures);
        log.info(
                "Missing Riot timeline sync completed: batches={}, scannedMatchIds={}, "
                        + "newTimelines={}, skippedItems={}, failures={}, durationMs={}",
                batchNumber,
                scannedMatchIds,
                persistedCount,
                skippedItems,
                failures.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        );
        return result;
    }

    private void logSyncCompleted(String batch, SyncResult result, long startedAtNanos) {
        log.info(
                "Riot match sync completed: batch={}, scannedPlayers={}, newMatches={}, "
                        + "newTimelines={}, skippedItems={}, failures={}, durationMs={}",
                batch,
                result.scannedPlayers(),
                result.newMatches(),
                result.newTimelines(),
                result.skippedItems(),
                result.failures().size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        );
    }

    private MatchCollection collectMatchIds(
            List<String> puuids,
            int matchStart,
            int matchCount,
            List<Failure> failures
    ) {
        if (puuids.isEmpty()) {
            return MatchCollection.empty();
        }

        List<PlayerCohortRepository.Target> latestCohorts =
                playerCohortRepository.findTargetsByPuuidsAndQueueType(puuids, QUEUE_TYPE);
        if (latestCohorts == null) {
            latestCohorts = List.of();
        }
        Map<String, PlayerCohortRepository.Target> cohortsByPuuid =
                latestCohorts
                        .stream()
                        .collect(Collectors.toMap(
                                PlayerCohortRepository.Target::getPuuid,
                                cohort -> cohort,
                                (first, ignored) -> first
                        ));
        LinkedHashSet<String> matchIds = new LinkedHashSet<>();
        Map<String, List<PlayerCohortRepository.Target>> cohortsByMatchId = new HashMap<>();

        for (String puuid : puuids) {
            List<String> puuidMatchIds;
            try {
                puuidMatchIds = riotClient.getMatchIds(puuid, matchStart, matchCount);
            } catch (RuntimeException exception) {
                recordFailure(failures, "MATCH_IDS", puuid, exception);
                continue;
            }
            matchIds.addAll(puuidMatchIds);
            PlayerCohortRepository.Target cohort = cohortsByPuuid.get(puuid);
            if (cohort == null) {
                continue;
            }
            puuidMatchIds.forEach(matchId ->
                    cohortsByMatchId.computeIfAbsent(matchId, ignored -> new ArrayList<>()).add(cohort)
            );
        }
        return new MatchCollection(
                puuids.size(),
                matchIds,
                cohortsByMatchId
        );
    }

    private ItemCollectionResult fetchAndPersist(
            String matchId,
            boolean matchAlreadyPersisted,
            boolean timelineAlreadyPersisted,
            List<PlayerCohortRepository.Target> cohorts,
            List<Failure> failures
    ) {
        int newMatches = 0;
        int newTimelines = 0;
        int skippedItems = 0;
        if (!matchAlreadyPersisted) {
            try {
                String rawData = riotClient.getRawMatch(matchId);
                if (persistenceService.persist(new RawMatch(matchId, rawData))) {
                    newMatches++;
                } else {
                    skippedItems++;
                }
            } catch (RuntimeException exception) {
                recordFailure(failures, "MATCH", matchId, exception);
                return new ItemCollectionResult(newMatches, newTimelines, skippedItems);
            }
        } else {
            skippedItems++;
        }
        if (!timelineAlreadyPersisted) {
            try {
                String rawData = riotClient.getRawMatchTimeline(matchId);
                if (timelinePersistenceService.persist(new RawMatchTimeline(matchId, rawData))) {
                    newTimelines++;
                } else {
                    skippedItems++;
                }
            } catch (RuntimeException exception) {
                recordFailure(failures, "TIMELINE", matchId, exception);
            }
        } else {
            skippedItems++;
        }
        Instant matchCollectedAt = Instant.now();
        for (PlayerCohortRepository.Target cohort : cohorts) {
            try {
                if (!cohortPersistenceService.persist(
                        new dfgg.domain.match.MatchParticipantCohort(
                                matchId,
                                cohort.getPuuid(),
                                cohort.getQueueType(),
                                cohort.getTier(),
                                cohort.getDivision(),
                                matchCollectedAt
                        )
                )) {
                    skippedItems++;
                }
            } catch (RuntimeException exception) {
                recordFailure(
                        failures,
                        "MATCH_COHORT",
                        matchId + "/" + cohort.getPuuid(),
                        exception
                );
            }
        }
        return new ItemCollectionResult(newMatches, newTimelines, skippedItems);
    }

    private void recordFailure(
            List<Failure> failures,
            String stage,
            String targetId,
            RuntimeException exception
    ) {
        Failure failure = Failure.from(stage, targetId, exception);
        failures.add(failure);
        log.warn(
                "Riot match collection item failed: stage={}, targetId={}, reason={}",
                failure.stage(),
                failure.targetId(),
                failure.reason()
        );
    }

    private record MatchCollection(
            int scannedPlayers,
            LinkedHashSet<String> matchIds,
            Map<String, List<PlayerCohortRepository.Target>> cohortsByMatchId
    ) {
        private static MatchCollection empty() {
            return new MatchCollection(0, new LinkedHashSet<>(), Map.of());
        }
    }

    private record ItemCollectionResult(int newMatches, int newTimelines, int skippedItems) {
    }

    public record SyncResult(
            int scannedPlayers,
            int newMatches,
            int newTimelines,
            int skippedItems,
            List<Failure> failures
    ) {

        public SyncResult {
            failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
        }

        private static SyncResult empty(int scannedPlayers, List<Failure> failures) {
            return new SyncResult(scannedPlayers, 0, 0, 0, failures);
        }
    }

    public record Failure(String stage, String targetId, String reason) {

        public Failure {
            Objects.requireNonNull(stage, "stage must not be null");
            Objects.requireNonNull(targetId, "targetId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }

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
