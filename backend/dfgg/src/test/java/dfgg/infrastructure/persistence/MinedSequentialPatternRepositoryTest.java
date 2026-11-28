package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MinedSequentialPatternRepositoryTest {

    @Autowired
    private MinedSequentialPatternRepository minedSequentialPatternRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("마이닝된 순차 패턴을 저장하고 조회한다")
    void save_WhenMinedSequentialPatternIsSaved_CanBeFoundById() {
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

    @Test
    @DisplayName("algorithmVersion이 일치하는 패턴만 삭제한다")
    @Sql("/sql/mined-sequential-pattern-repository-test-data.sql")
    void deleteByAlgorithmVersion_WhenVersionMatches_DeletesOnlyThatVersion() {
        // given: data.sql이 v1 패턴 2건, v2 패턴 1건을 적재해둔다

        // when
        minedSequentialPatternRepository.deleteByAlgorithmVersion("v1");
        entityManager.flush();
        entityManager.clear();

        // then
        List<MinedSequentialPattern> remaining = minedSequentialPatternRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getAlgorithmVersion()).isEqualTo("v2");
    }

    @Test
    @DisplayName("algorithmVersion이 일치하는 패턴 개수만 센다")
    @Sql("/sql/mined-sequential-pattern-repository-test-data.sql")
    void countByAlgorithmVersion_WhenVersionMatches_CountsOnlyThatVersion() {
        // given: data.sql이 v1 패턴 2건, v2 패턴 1건을 적재해둔다

        // when & then
        assertThat(minedSequentialPatternRepository.countByAlgorithmVersion("v1")).isEqualTo(2);
        assertThat(minedSequentialPatternRepository.countByAlgorithmVersion("v2")).isEqualTo(1);
        assertThat(minedSequentialPatternRepository.countByAlgorithmVersion("v3")).isEqualTo(0);
    }

    @Test
    @DisplayName("스코프(챔피언, 포지션, 티어, 패치)와 algorithmVersion이 모두 일치하는 패턴만 조회한다")
    @Sql("/sql/mined-sequential-pattern-repository-test-data.sql")
    void findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch_WhenScopeMatches_ReturnsOnlyMatchingPatterns() {
        // given: data.sql이 (championId=266, TOP, GOLD, 14.1, v1) 패턴 2건과
        // (championId=99, MID, GOLD, 14.1, v2) 패턴 1건을 적재해둔다

        // when
        List<MinedSequentialPattern> found = minedSequentialPatternRepository
                .findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                        "v1", 266L, ChampionPosition.TOP, "GOLD", "14.1"
                );

        // then
        assertThat(found).hasSize(2);
        assertThat(found).allMatch(pattern -> pattern.getChampionId().equals(266L));
        assertThat(found).allMatch(pattern -> pattern.getAlgorithmVersion().equals("v1"));
    }

    @Test
    @DisplayName("스코프가 일치하지 않으면 빈 리스트를 반환한다")
    @Sql("/sql/mined-sequential-pattern-repository-test-data.sql")
    void findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch_WhenScopeDoesNotMatch_ReturnsEmptyList() {
        // given: data.sql에는 championId=266인 패턴이 GOLD 티어로만 존재한다

        // when
        List<MinedSequentialPattern> found = minedSequentialPatternRepository
                .findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                        "v1", 266L, ChampionPosition.TOP, "PLATINUM", "14.1"
                );

        // then
        assertThat(found).isEmpty();
    }
}
