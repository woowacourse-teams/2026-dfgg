package dfgg.application.match;

import dfgg.infrastructure.external.client.RiotClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집할 플레이어의 매치 ID를 찾고 Raw Match와 Raw Timeline 수집 순서를 조율한다.
 * 정규화와 통계 집계는 이 서비스의 책임이 아니며
 * {@link dfgg.application.RiotCollectionOrchestrator}가 후속 단계로 호출한다.
 */
@Service
public class RiotMatchSyncService {

    private final RiotClient riotClient;
    private final RawMatchService rawMatchService;
    private final RawMatchTimelineService rawMatchTimelineService;

    public RiotMatchSyncService(
            RiotClient riotClient,
            RawMatchService rawMatchService,
            RawMatchTimelineService rawMatchTimelineService
    ) {
        this.riotClient = riotClient;
        this.rawMatchService = rawMatchService;
        this.rawMatchTimelineService = rawMatchTimelineService;
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
    public SyncResult syncMissingTimelines() {
        RawMatchTimelineService.MissingTimelineSyncResult result =
                rawMatchTimelineService.collectMissingTimelines();
        List<Failure> failures = result.failures().stream()
                .map(failure -> new Failure("TIMELINE", failure.matchId(), failure.reason()))
                .toList();
        return new SyncResult(
                0,
                0,
                result.newTimelines(),
                result.skippedItems(),
                failures
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

        LinkedHashSet<String> matchIds = new LinkedHashSet<>();

        for (String puuid : puuids) {
            List<String> puuidMatchIds;
            try {
                puuidMatchIds = riotClient.getMatchIds(puuid, matchStart, matchCount);
            } catch (RuntimeException exception) {
                recordFailure(failures, "MATCH_IDS", puuid, exception);
                continue;
            }
            matchIds.addAll(puuidMatchIds);
        }
        return new MatchCollection(
                puuids.size(),
                matchIds
        );
    }

    private MatchCollectResult collectMatch(
            String matchId,
            boolean matchAlreadyPersisted,
            boolean timelineAlreadyPersisted,
            List<Failure> failures
    ) {
        int newMatches = 0;
        int newTimelines = 0;
        int skippedItems = 0;

        // Timeline보다 Match 원본을 먼저 저장한다. Match 수집이 실패하면 이 매치의 Timeline은 수집하지 않는다.
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
            LinkedHashSet<String> matchIds
    ) {
        private static MatchCollection empty() {
            return new MatchCollection(0, new LinkedHashSet<>());
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
