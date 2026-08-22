package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
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
    void 임베딩을_저장하고_조회한다() {
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
    @Sql("/sql/embedding-repository-test-data.sql")
    void entityType으로_구분해_조회한다() {
        // given: data.sql이 CHAMPION 임베딩 1건, ITEM 임베딩 1건을 미리 적재해둔다

        // when
        List<Embedding> championEmbeddings = embeddingRepository.findByEntityType(EmbeddingEntityType.CHAMPION);

        // then
        assertThat(championEmbeddings).hasSize(1);
        assertThat(championEmbeddings.get(0).getEntityId()).isEqualTo(266L);
    }

    @Test
    void 같은_entityType_entityId_algorithmVersion_조합은_중복_저장할_수_없다() {
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
