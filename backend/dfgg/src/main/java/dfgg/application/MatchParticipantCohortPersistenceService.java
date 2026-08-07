package dfgg.application;

import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchParticipantCohortPersistenceService {

    private final MatchParticipantCohortRepository repository;

    public MatchParticipantCohortPersistenceService(MatchParticipantCohortRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean persist(MatchParticipantCohort cohort) {
        Objects.requireNonNull(cohort, "cohort must not be null");
        return repository.insertIfAbsent(
                cohort.getMatchId(),
                cohort.getPuuid(),
                cohort.getQueueType(),
                cohort.getTier(),
                cohort.getDivision(),
                cohort.getCollectedAt()
        ) == 1;
    }
}
