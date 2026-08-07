package dfgg.application;

import dfgg.domain.player.PlayerCohort;
import dfgg.domain.player.PlayerCohortRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerCohortPersistenceService {

    private final PlayerCohortRepository repository;

    public PlayerCohortPersistenceService(PlayerCohortRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void persist(PlayerCohort cohort) {
        Objects.requireNonNull(cohort, "cohort must not be null");
        repository.upsert(
                cohort.getPuuid(),
                cohort.getQueueType(),
                cohort.getTier(),
                cohort.getDivision(),
                cohort.getCollectedAt()
        );
    }
}
