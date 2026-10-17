package dfgg.application.match;

import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.util.Collection;
import java.util.List;
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
    public void collectMissingTimelines() {
        RuntimeException firstFailure = null;
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
                    collectRawMatchTimeline(matchId);
                } catch (RuntimeException exception) {
                    if (firstFailure == null) {
                        firstFailure = exception;
                    }
                }
            }
            cursor = matchIds.getLast();
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
