package dfgg.domain.rune;

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

    private static final RunePage JAYCE_PAGE = new RunePage(8400, List.of(8437, 8446, 8473, 8451),
            8300, List.of(8304, 8345), new StatShards(5005, 5008, 5001));
    private static final RunePage JAYCE_PHASE_RUSH_PAGE = new RunePage(8200, List.of(8230, 8226, 8233, 8236),
            8300, List.of(8304, 8345), new StatShards(5005, 5008, 5001));
    private static final RunePage JAX_PAGE = new RunePage(8000, List.of(8010, 9111, 9104, 8299),
            8400, List.of(8444, 8242), new StatShards(5005, 5008, 5001));

    @Autowired
    private NormalizedMatchParticipantRuneRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("칸에 펼쳐 저장한 룬 페이지를 같은 선택 순서로 다시 읽는다")
    void save_RestoresRunePageInSelectionOrder() {
        // given
        repository.save(jayce(JAYCE_PAGE));
        entityManager.flush();
        entityManager.clear();

        // when
        List<NormalizedMatchParticipantRune> saved = repository.findAll();

        // then
        assertThat(saved).singleElement()
                .extracting(NormalizedMatchParticipantRune::runePage)
                .isEqualTo(JAYCE_PAGE);
    }

    @Test
    @DisplayName("한 경기의 참가자 룬을 한 번에 저장한다")
    void upsertAll_InsertsAllParticipants() {
        // when
        repository.upsertAll(List.of(jayce(JAYCE_PAGE), jax(JAX_PAGE)));

        // then
        assertThat(repository.findAll())
                .extracting(NormalizedMatchParticipantRune::runePage)
                .containsExactlyInAnyOrder(JAYCE_PAGE, JAX_PAGE);
    }

    @Test
    @DisplayName("같은 경기·참가자가 이미 있으면 새 행을 만들지 않고 룬을 덮어쓴다")
    void upsertAll_WhenParticipantExists_UpdatesRunePage() {
        // given
        repository.upsertAll(List.of(jayce(JAYCE_PAGE), jax(JAX_PAGE)));

        // when
        repository.upsertAll(List.of(jayce(JAYCE_PHASE_RUSH_PAGE), jax(JAX_PAGE)));

        // then
        assertThat(repository.findAll())
                .extracting(NormalizedMatchParticipantRune::runePage)
                .containsExactlyInAnyOrder(JAYCE_PHASE_RUSH_PAGE, JAX_PAGE);
    }

    @Test
    @DisplayName("저장할 룬이 없으면 아무것도 하지 않는다")
    void upsertAll_WhenEmpty_DoesNothing() {
        // when
        repository.upsertAll(List.of());

        // then
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("룬 기록에 있는 패치를 중복 없이 조회한다")
    void findDistinctPatches_ReturnsEachPatchOnce() {
        // given
        repository.upsertAll(List.of(jayce(JAYCE_PAGE), jax(JAX_PAGE),
                new NormalizedMatchParticipantRune("KR_2", 1, "16.20", 126, 100, "TOP", true, JAYCE_PAGE)));

        // when & then
        assertThat(repository.findDistinctPatches()).containsExactlyInAnyOrder("16.19", "16.20");
    }

    @Test
    @DisplayName("패치·챔피언·포지션이 모두 같은 룬 기록만 조회한다")
    void findByChampionIdAndPositionAndPatch_ReturnsOnlyMatchingRecords() {
        // given
        repository.upsertAll(List.of(
                jayce(JAYCE_PAGE),
                jax(JAX_PAGE),
                new NormalizedMatchParticipantRune("KR_2", 1, "16.20", 126, 100, "TOP", true, JAYCE_PHASE_RUSH_PAGE),
                new NormalizedMatchParticipantRune("KR_3", 2, "16.19", 126, 100, "MIDDLE", true, JAYCE_PHASE_RUSH_PAGE)
        ));

        // when
        List<NormalizedMatchParticipantRune> found =
                repository.findByChampionIdAndPositionAndPatch(126, "TOP", "16.19");

        // then
        assertThat(found).extracting(NormalizedMatchParticipantRune::runePage).containsExactly(JAYCE_PAGE);
    }

    private NormalizedMatchParticipantRune jayce(RunePage runePage) {
        return new NormalizedMatchParticipantRune("KR_1", 1, "16.19", 126, 100, "TOP", true, runePage);
    }

    private NormalizedMatchParticipantRune jax(RunePage runePage) {
        return new NormalizedMatchParticipantRune("KR_1", 6, "16.19", 24, 200, "TOP", false, runePage);
    }
}
