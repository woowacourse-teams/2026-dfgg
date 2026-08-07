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
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class RiotMatchSyncService {

    private static final String PLATFORM = "KR";
    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";

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
    public void syncMatches(
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

        MatchCollection matchCollection = collectMatchIds(
                playerPage,
                playerCount,
                matchStart,
                matchCount
        );
        LinkedHashSet<String> matchIds = matchCollection.matchIds();
        if (matchIds.isEmpty()) {
            return;
        }

        Set<String> existingMatchIds = rawMatchRepository.findExistingMatchIds(matchIds);
        Set<String> existingTimelineIds = rawMatchTimelineRepository.findExistingMatchIds(matchIds);

        matchIds.forEach(matchId -> fetchAndPersist(
                matchId,
                existingMatchIds.contains(matchId),
                existingTimelineIds.contains(matchId),
                matchCollection.cohortsByMatchId().getOrDefault(matchId, List.of())
        ));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int syncMissingTimelines() {
        List<RawMatch> rawMatches = rawMatchRepository.findAll();
        if (rawMatches.isEmpty()) {
            return 0;
        }

        LinkedHashSet<String> matchIds = rawMatches.stream()
                .map(RawMatch::getMatchId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> existingTimelineIds = rawMatchTimelineRepository.findExistingMatchIds(matchIds);
        int persistedCount = 0;
        for (RawMatch rawMatch : rawMatches) {
            if (existingTimelineIds.contains(rawMatch.getMatchId())) {
                continue;
            }
            String rawData = riotClient.getRawMatchTimeline(rawMatch.getMatchId());
            if (timelinePersistenceService.persist(
                    new RawMatchTimeline(rawMatch.getMatchId(), rawData)
            )) {
                persistedCount++;
            }
        }
        return persistedCount;
    }

    private MatchCollection collectMatchIds(
            int playerPage,
            int playerCount,
            int matchStart,
            int matchCount
    ) {
        List<String> puuids = playerRepository.findPuuidsByPlatform(
                PLATFORM,
                PageRequest.of(playerPage, playerCount)
        );
        if (puuids.isEmpty()) {
            return new MatchCollection(new LinkedHashSet<>(), Map.of());
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

        puuids.forEach(puuid -> {
            List<String> puuidMatchIds = riotClient.getMatchIds(puuid, matchStart, matchCount);
            matchIds.addAll(puuidMatchIds);
            PlayerCohortRepository.Target cohort = cohortsByPuuid.get(puuid);
            if (cohort == null) {
                return;
            }
            puuidMatchIds.forEach(matchId ->
                    cohortsByMatchId.computeIfAbsent(matchId, ignored -> new ArrayList<>()).add(cohort)
            );
        });
        return new MatchCollection(matchIds, cohortsByMatchId);
    }

    private void fetchAndPersist(
            String matchId,
            boolean matchAlreadyPersisted,
            boolean timelineAlreadyPersisted,
            List<PlayerCohortRepository.Target> cohorts
    ) {
        if (!matchAlreadyPersisted) {
            String rawData = riotClient.getRawMatch(matchId);
            persistenceService.persist(new RawMatch(matchId, rawData));
        }
        if (!timelineAlreadyPersisted) {
            String rawData = riotClient.getRawMatchTimeline(matchId);
            timelinePersistenceService.persist(new RawMatchTimeline(matchId, rawData));
        }
        Instant matchCollectedAt = Instant.now();
        cohorts.forEach(cohort -> cohortPersistenceService.persist(
                new dfgg.domain.match.MatchParticipantCohort(
                        matchId,
                        cohort.getPuuid(),
                        cohort.getQueueType(),
                        cohort.getTier(),
                        cohort.getDivision(),
                        matchCollectedAt
                )
        ));
    }

    private record MatchCollection(
            LinkedHashSet<String> matchIds,
            Map<String, List<PlayerCohortRepository.Target>> cohortsByMatchId
    ) {
    }
}
