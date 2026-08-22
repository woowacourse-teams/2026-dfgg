package dfgg.application.match;

import dfgg.application.MatchParticipantCohortPersistenceService;
import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiotMatchSyncService {

    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";

    private final RiotClient riotClient;
    private final RawMatchService rawMatchService;
    private final RawMatchTimelineService rawMatchTimelineService;
    private final PlayerRepository playerRepository;
    private final MatchParticipantCohortPersistenceService cohortPersistenceService;

    public RiotMatchSyncService(
            RiotClient riotClient,
            RawMatchService rawMatchService,
            RawMatchTimelineService rawMatchTimelineService,
            PlayerRepository playerRepository,
            MatchParticipantCohortPersistenceService cohortPersistenceService
    ) {
        this.riotClient = riotClient;
        this.rawMatchService = rawMatchService;
        this.rawMatchTimelineService = rawMatchTimelineService;
        this.playerRepository = playerRepository;
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
                    matchCollection.playersByMatchId().getOrDefault(matchId, List.of()),
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

        Map<String, Player> playersByPuuid = new HashMap<>();
        for (Player player : playerRepository.findAllById(puuids)) {
            playersByPuuid.putIfAbsent(player.getPuuid(), player);
        }
        LinkedHashSet<String> matchIds = new LinkedHashSet<>();
        Map<String, List<Player>> playersByMatchId = new HashMap<>();

        for (String puuid : puuids) {
            List<String> puuidMatchIds;
            try {
                puuidMatchIds = riotClient.getMatchIds(puuid, matchStart, matchCount);
            } catch (RuntimeException exception) {
                recordFailure(failures, "MATCH_IDS", puuid, exception);
                continue;
            }
            matchIds.addAll(puuidMatchIds);
            Player player = playersByPuuid.get(puuid);
            if (player == null) {
                continue;
            }
            puuidMatchIds.forEach(matchId ->
                    playersByMatchId.computeIfAbsent(matchId, ignored -> new ArrayList<>()).add(player)
            );
        }
        return new MatchCollection(
                puuids.size(),
                matchIds,
                playersByMatchId
        );
    }

    private MatchCollectResult collectMatch(
            String matchId,
            boolean matchAlreadyPersisted,
            boolean timelineAlreadyPersisted,
            List<Player> players,
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
        for (Player player : players) {
            MatchParticipantCohort matchCohort = new MatchParticipantCohort(
                    matchId,
                    player.getPuuid(),
                    QUEUE_TYPE,
                    player.getTier(),
                    player.getDivision(),
                    matchCollectedAt
            );
            try {
                if (!cohortPersistenceService.persist(matchCohort)) {
                    skippedItems++;
                }
            } catch (RuntimeException exception) {
                recordFailure(
                        failures,
                        "MATCH_COHORT",
                        matchId + "/" + player.getPuuid(),
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
            Map<String, List<Player>> playersByMatchId
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
