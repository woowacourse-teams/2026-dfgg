package dfgg.application;

import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RawMatchTimelinePersistenceService {

    private final RawMatchTimelineRepository rawMatchTimelineRepository;

    public RawMatchTimelinePersistenceService(RawMatchTimelineRepository rawMatchTimelineRepository) {
        this.rawMatchTimelineRepository = rawMatchTimelineRepository;
    }

    @Transactional
    public boolean persist(RawMatchTimeline timeline) {
        Objects.requireNonNull(timeline, "timeline must not be null");

        return rawMatchTimelineRepository.insertIfAbsent(
                timeline.getMatchId(),
                timeline.getRawData()
        ) == 1;
    }
}
