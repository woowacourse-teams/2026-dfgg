package dfgg.domain.embedding;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {

    List<Embedding> findByEntityType(EmbeddingEntityType entityType);
}
