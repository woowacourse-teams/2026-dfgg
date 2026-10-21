package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class NormalizedMatchParticipantRuneRepositoryTest {

    @Autowired
    private NormalizedMatchParticipantRuneRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("칸에 펼쳐 저장한 룬 페이지를 같은 선택 순서로 다시 읽는다")
    void save_RestoresRunePageInSelectionOrder() {
        // given
        RunePage runePage = new RunePage(8400, List.of(8437, 8446, 8473, 8451),
                8300, List.of(8304, 8345), new StatShards(5005, 5008, 5001));
        repository.save(new NormalizedMatchParticipantRune("KR_1", 1, "16.19", 126, 100, "TOP", true, runePage));
        entityManager.flush();
        entityManager.clear();

        // when
        List<NormalizedMatchParticipantRune> saved = repository.findAll();

        // then
        assertThat(saved).singleElement()
                .extracting(NormalizedMatchParticipantRune::runePage)
                .isEqualTo(runePage);
    }
}
