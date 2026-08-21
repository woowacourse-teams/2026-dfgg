package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MinedSequentialPatternRepositoryTest {

    @Autowired
    private MinedSequentialPatternRepository minedSequentialPatternRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 마이닝된_순차_패턴을_저장하고_조회한다() {
        // given
        MinedSequentialPattern pattern = new MinedSequentialPattern(
                266L,
                ChampionPosition.TOP,
                "GOLD",
                "14.1",
                List.of(3071L, 6653L),
                42,
                100,
                25,
                "v1"
        );

        // when
        MinedSequentialPattern saved = minedSequentialPatternRepository.save(pattern);
        entityManager.flush();
        entityManager.clear();

        // then
        MinedSequentialPattern found = minedSequentialPatternRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getChampionId()).isEqualTo(266L);
        assertThat(found.getPosition()).isEqualTo(ChampionPosition.TOP);
        assertThat(found.getTier()).isEqualTo("GOLD");
        assertThat(found.getPatch()).isEqualTo("14.1");
        assertThat(found.getItems()).containsExactly(3071L, 6653L);
        assertThat(found.getPatternKey()).isEqualTo("3071-6653");
        assertThat(found.getSupportCount()).isEqualTo(42);
        assertThat(found.getScopeTotalCount()).isEqualTo(100);
        assertThat(found.getWinCount()).isEqualTo(25);
        assertThat(found.getAlgorithmVersion()).isEqualTo("v1");
    }
}
