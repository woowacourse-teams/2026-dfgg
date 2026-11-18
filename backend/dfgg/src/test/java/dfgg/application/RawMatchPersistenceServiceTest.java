package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
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
@Import(RawMatchPersistenceService.class)
class RawMatchPersistenceServiceTest {

    @Autowired
    private RawMatchPersistenceService persistenceService;

    @Autowired
    private RawMatchRepository rawMatchRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 매치_원본은_한_번만_저장한다() {
        RawMatch rawMatch = new RawMatch("KR_1234567890", "{\"info\":{}}");

        boolean firstPersisted = persistenceService.persist(rawMatch);
        boolean secondPersisted = persistenceService.persist(rawMatch);
        entityManager.flush();
        entityManager.clear();

        assertThat(firstPersisted).isTrue();
        assertThat(secondPersisted).isFalse();
        assertThat(rawMatchRepository.count()).isEqualTo(1);
    }
}
