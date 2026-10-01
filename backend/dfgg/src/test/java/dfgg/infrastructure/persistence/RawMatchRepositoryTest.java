package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RawMatchRepositoryTest {

    private static final String MATCH_ID = "KR_1234567890";
    private static final String RAW_DATA = """
            {
              "info": {
                "participants": [
                  {
                    "puuid": "encrypted-puuid",
                    "championId": 266,
                    "teamId": 100,
                    "teamPosition": "TOP",
                    "item0": 3071,
                    "win": true
                  }
                ]
              }
            }
            """;

    @Autowired
    private RawMatchRepository rawMatchRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 매치_원본_데이터를_저장하고_조회한다() {
        rawMatchRepository.save(new RawMatch(MATCH_ID, RAW_DATA));
        entityManager.flush();
        entityManager.clear();

        RawMatch saved = rawMatchRepository.findById(MATCH_ID).orElseThrow();

        assertThat(saved.getMatchId()).isEqualTo(MATCH_ID);
        assertThat(saved.getRawData()).isEqualTo(RAW_DATA);
    }

    @Test
    void 같은_매치_ID의_원본_데이터는_중복_저장할_수_없다() {
        rawMatchRepository.save(new RawMatch(MATCH_ID, RAW_DATA));
        entityManager.flush();
        entityManager.clear();

        entityManager.persist(new RawMatch(MATCH_ID, RAW_DATA));

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(PersistenceException.class);
    }
}
