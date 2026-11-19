package dfgg.domain.embedding;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {

    List<Embedding> findByEntityType(EmbeddingEntityType entityType);

    void deleteByAlgorithmVersion(String algorithmVersion);

    long countByAlgorithmVersion(String algorithmVersion);

    List<Embedding> findByAlgorithmVersionAndEntityType(String algorithmVersion, EmbeddingEntityType entityType);

    List<Embedding> findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
            String algorithmVersion,
            EmbeddingEntityType entityType,
            Collection<Long> entityIds
    );
}
