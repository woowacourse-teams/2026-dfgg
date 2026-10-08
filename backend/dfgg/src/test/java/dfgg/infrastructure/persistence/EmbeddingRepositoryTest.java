package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmbeddingRepositoryTest {

    @Autowired
    private EmbeddingRepository embeddingRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("임베딩을 저장하고 조회한다")
    void save_WhenEmbeddingIsSaved_CanBeFoundById() {
        // given
        Embedding embedding = new Embedding(
                EmbeddingEntityType.CHAMPION, 266L, "v1", List.of(0.1, 0.2, 0.3), LocalDateTime.now()
        );

        // when
        Embedding saved = embeddingRepository.save(embedding);
        entityManager.flush();
        entityManager.clear();

        // then
        Embedding found = embeddingRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEntityType()).isEqualTo(EmbeddingEntityType.CHAMPION);
        assertThat(found.getEntityId()).isEqualTo(266L);
        assertThat(found.getAlgorithmVersion()).isEqualTo("v1");
        assertThat(found.getVector()).containsExactly(0.1, 0.2, 0.3);
    }

    @Test
    @DisplayName("entityType으로 구분해 조회한다")
    @Sql("/sql/embedding-repository-test-data.sql")
    void findByEntityType_WhenEntityTypeGiven_ReturnsOnlyMatchingEmbeddings() {
        // given: data.sql이 CHAMPION 임베딩 1건, ITEM 임베딩 1건을 미리 적재해둔다

        // when
        List<Embedding> championEmbeddings = embeddingRepository.findByEntityType(EmbeddingEntityType.CHAMPION);

        // then
        assertThat(championEmbeddings).hasSize(1);
        assertThat(championEmbeddings.get(0).getEntityId()).isEqualTo(266L);
    }

    @Test
    @DisplayName("algorithmVersion이 일치하는 임베딩만 삭제한다")
    @Sql("/sql/embedding-repository-test-data.sql")
    void deleteByAlgorithmVersion_WhenVersionMatches_DeletesOnlyThatVersion() {
        // given: data.sql이 v1 임베딩 2건, v2 임베딩 1건을 적재해둔다

        // when
        embeddingRepository.deleteByAlgorithmVersion("v1");
        entityManager.flush();
        entityManager.clear();

        // then
        List<Embedding> remaining = embeddingRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getAlgorithmVersion()).isEqualTo("v2");
    }

    @Test
    @DisplayName("algorithmVersion이 일치하는 임베딩 개수만 센다")
    @Sql("/sql/embedding-repository-test-data.sql")
    void countByAlgorithmVersion_WhenVersionMatches_CountsOnlyThatVersion() {
        // given: data.sql이 v1 임베딩 2건, v2 임베딩 1건을 적재해둔다

        // when & then
        assertThat(embeddingRepository.countByAlgorithmVersion("v1")).isEqualTo(2);
        assertThat(embeddingRepository.countByAlgorithmVersion("v2")).isEqualTo(1);
        assertThat(embeddingRepository.countByAlgorithmVersion("v3")).isEqualTo(0);
    }

    @Test
    @DisplayName("algorithmVersion과 entityType이 모두 일치하는 임베딩만 조회한다")
    @Sql("/sql/embedding-repository-test-data.sql")
    void findByAlgorithmVersionAndEntityType_WhenVersionAndTypeMatch_ReturnsOnlyMatchingEmbeddings() {
        // given: data.sql이 v1-CHAMPION(entityId=266) 1건, v1-ITEM(entityId=3071) 1건,
        // v2-ITEM(entityId=9999) 1건을 적재해둔다

        // when
        List<Embedding> found = embeddingRepository.findByAlgorithmVersionAndEntityType(
                "v1", EmbeddingEntityType.ITEM
        );

        // then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getAlgorithmVersion()).isEqualTo("v1");
        assertThat(found.get(0).getEntityId()).isEqualTo(3071L);
    }

    @Test
    @DisplayName("algorithmVersion, entityType, entityId 목록이 모두 일치하는 임베딩만 조회한다")
    @Sql("/sql/embedding-repository-test-data.sql")
    void findByAlgorithmVersionAndEntityTypeAndEntityIdIn_WhenIdsGiven_ReturnsOnlyMatchingEmbeddings() {
        // given: data.sql이 v1-CHAMPION(entityId=266) 1건, v1-ITEM(entityId=3071) 1건,
        // v2-ITEM(entityId=9999) 1건을 적재해둔다

        // when
        List<Embedding> found = embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                "v1", EmbeddingEntityType.CHAMPION, List.of(266L, 999L)
        );

        // then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getEntityId()).isEqualTo(266L);
    }

    @Test
    @DisplayName("같은 entityType, entityId, algorithmVersion 조합은 중복 저장할 수 없다")
    void save_WhenEntityTypeAndEntityIdAndAlgorithmVersionDuplicate_ThrowsDataIntegrityViolationException() {
        // given
        embeddingRepository.save(new Embedding(
                EmbeddingEntityType.CHAMPION, 266L, "v1", List.of(0.1, 0.2), LocalDateTime.now()
        ));
        entityManager.flush();

        // when & then
        assertThatThrownBy(() -> {
            embeddingRepository.save(new Embedding(
                    EmbeddingEntityType.CHAMPION, 266L, "v1", List.of(0.9, 0.9), LocalDateTime.now()
            ));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
