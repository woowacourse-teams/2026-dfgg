package dfgg.application.match;

import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RawMatchTimelineService {

    private static final int TIMELINE_BATCH_SIZE = 100;

    private final RiotClient riotClient;
    private final RawMatchRepository rawMatchRepository;
    private final RawMatchTimelineRepository rawMatchTimelineRepository;

    public RawMatchTimelineService(
            RiotClient riotClient,
            RawMatchRepository rawMatchRepository,
            RawMatchTimelineRepository rawMatchTimelineRepository
    ) {
        this.riotClient = riotClient;
        this.rawMatchRepository = rawMatchRepository;
        this.rawMatchTimelineRepository = rawMatchTimelineRepository;
    }

    public Set<String> findExistingMatchIds(Collection<String> matchIds) {
        return rawMatchTimelineRepository.findExistingMatchIds(matchIds);
    }

    public boolean collectRawMatchTimeline(String matchId) {
        String rawData = riotClient.getRawMatchTimeline(matchId);
        RawMatchTimeline timeline = new RawMatchTimeline(matchId, rawData);

        return rawMatchTimelineRepository.insertIfAbsent(
                timeline.getMatchId(),
                timeline.getRawData()
        ) == 1;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MissingTimelineSyncResult collectMissingTimelines() {
        int newTimelines = 0;
        int skippedItems = 0;
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
            for (String matchId : matchIds) {
                try {
                    if (collectRawMatchTimeline(matchId)) {
                        newTimelines++;
                    } else {
                        skippedItems++;
                    }
                } catch (RuntimeException exception) {
                    failures.add(Failure.from(matchId, exception));
                }
            }
            cursor = matchIds.getLast();
        }
        return new MissingTimelineSyncResult(newTimelines, skippedItems, failures);
    }

    public record MissingTimelineSyncResult(
            int newTimelines,
            int skippedItems,
            List<Failure> failures
    ) {

        public MissingTimelineSyncResult {
            failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
        }
    }

    public record Failure(String matchId, String reason) {

        public Failure {
            Objects.requireNonNull(matchId, "matchId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }

        private static Failure from(String matchId, RuntimeException exception) {
            String type = exception.getClass().getSimpleName();
            String message = exception.getMessage();
            String reason = message == null || message.isBlank() ? type : type + ": " + message;
            return new Failure(matchId, reason);
        }
    }
}
