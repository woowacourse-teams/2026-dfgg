package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RawMatchTimelinePersistenceService.class)
class RawMatchTimelinePersistenceServiceTest {

    @Autowired
    private RawMatchTimelinePersistenceService persistenceService;

    @Autowired
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 매치_Timeline_원본은_한_번만_저장한다() {
        RawMatchTimeline timeline = new RawMatchTimeline(
                "KR_1234567890",
                "{\"info\":{\"frames\":[]}}"
        );

        boolean firstPersisted = persistenceService.persist(timeline);
        boolean secondPersisted = persistenceService.persist(timeline);
        entityManager.flush();
        entityManager.clear();

        assertThat(firstPersisted).isTrue();
        assertThat(secondPersisted).isFalse();
        assertThat(rawMatchTimelineRepository.count()).isEqualTo(1);
    }
}
