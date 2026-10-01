package dfgg.domain.embedding;

import dfgg.infrastructure.persistence.DoubleListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "embeddings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_embeddings_entity_algorithm",
                columnNames = {"entity_type", "entity_id", "algorithm_version"}
        )
)
public class Embedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "embedding_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 16)
    private EmbeddingEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "algorithm_version", nullable = false, length = 32)
    private String algorithmVersion;

    @Convert(converter = DoubleListConverter.class)
    @Column(name = "vector", nullable = false, columnDefinition = "TEXT")
    private List<Double> vector;

    @Column(name = "trained_at", nullable = false)
    private LocalDateTime trainedAt;

    protected Embedding() {
    }

    public Embedding(
            EmbeddingEntityType entityType,
            Long entityId,
            String algorithmVersion,
            List<Double> vector,
            LocalDateTime trainedAt
    ) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.algorithmVersion = algorithmVersion;
        this.vector = new ArrayList<>(vector);
        this.trainedAt = trainedAt;
    }

    public Long getId() {
        return id;
    }

    public EmbeddingEntityType getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public List<Double> getVector() {
        return List.copyOf(vector);
    }

    public LocalDateTime getTrainedAt() {
        return trainedAt;
    }
}
