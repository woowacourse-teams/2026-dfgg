package dfgg.application.match;

import dfgg.application.MatchParticipantCohortPersistenceService;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiotMatchSyncService {

    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";

    private final RiotClient riotClient;
    private final RawMatchService rawMatchService;
    private final RawMatchTimelineService rawMatchTimelineService;
    private final PlayerCohortRepository playerCohortRepository;
    private final MatchParticipantCohortPersistenceService cohortPersistenceService;

    public RiotMatchSyncService(
            RiotClient riotClient,
            RawMatchService rawMatchService,
            RawMatchTimelineService rawMatchTimelineService,
            PlayerCohortRepository playerCohortRepository,
            MatchParticipantCohortPersistenceService cohortPersistenceService
    ) {
        this.riotClient = riotClient;
        this.rawMatchService = rawMatchService;
        this.rawMatchTimelineService = rawMatchTimelineService;
        this.playerCohortRepository = playerCohortRepository;
        this.cohortPersistenceService = cohortPersistenceService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SyncResult syncMatches(
            List<String> puuids,
            int matchStart,
            int matchCount
    ) {
        List<String> distinctPuuids = new ArrayList<>(new LinkedHashSet<>(puuids));
        if (distinctPuuids.isEmpty()) {
            return SyncResult.empty(0, List.of());
        }
        return collectMatches(distinctPuuids, matchStart, matchCount);
    }

    private SyncResult collectMatches(
            List<String> puuids,
            int matchStart,
            int matchCount
    ) {
        List<Failure> failures = new ArrayList<>();
        MatchCollection matchCollection = collectMatchIds(puuids, matchStart, matchCount, failures);
        LinkedHashSet<String> matchIds = matchCollection.matchIds();
        if (matchIds.isEmpty()) {
            return SyncResult.empty(matchCollection.scannedPlayers(), failures);
        }

        Set<String> existingMatchIds = rawMatchService.findExistingMatchIds(matchIds);
        Set<String> existingTimelineIds = rawMatchTimelineService.findExistingMatchIds(matchIds);

        int newMatches = 0;
        int newTimelines = 0;
        int skippedItems = 0;
        for (String matchId : matchIds) {
            MatchCollectResult result = collectMatch(
                    matchId,
                    existingMatchIds.contains(matchId),
                    existingTimelineIds.contains(matchId),
                    matchCollection.cohortsByMatchId().getOrDefault(matchId, List.of()),
                    failures
            );
            newMatches += result.newMatches();
            newTimelines += result.newTimelines();
            skippedItems += result.skippedItems();
        }
        return new SyncResult(
                matchCollection.scannedPlayers(),
                newMatches,
                newTimelines,
                skippedItems,
                failures
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int syncMissingTimelines() {
        return rawMatchTimelineService.collectMissingTimelines().newTimelines();
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

    private MatchCollectResult collectMatch(
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
                if (rawMatchService.collectRawMatch(matchId)) {
                    newMatches++;
                } else {
                    skippedItems++;
                }
            } catch (RuntimeException exception) {
                recordFailure(failures, "MATCH", matchId, exception);
                return new MatchCollectResult(newMatches, newTimelines, skippedItems);
            }
        } else {
            skippedItems++;
        }
        if (!timelineAlreadyPersisted) {
            try {
                if (rawMatchTimelineService.collectRawMatchTimeline(matchId)) {
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
        return new MatchCollectResult(newMatches, newTimelines, skippedItems);
    }

    private void recordFailure(
            List<Failure> failures,
            String stage,
            String targetId,
            RuntimeException exception
    ) {
        Failure failure = Failure.from(stage, targetId, exception);
        failures.add(failure);
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

    private record MatchCollectResult(int newMatches, int newTimelines, int skippedItems) {
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
