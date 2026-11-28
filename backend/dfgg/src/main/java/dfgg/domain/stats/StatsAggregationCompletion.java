package dfgg.domain.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "stats_aggregation_completions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stats_aggregation_completion_target",
                columnNames = {"match_id", "puuid", "queue_type", "tier", "aggregation_revision"}
        ),
        indexes = @Index(
                name = "idx_stats_aggregation_completion_lookup",
                columnList = "queue_type,tier,aggregation_revision,match_id,puuid"
        )
)
public class StatsAggregationCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, length = 32)
    private String matchId;

    @Column(name = "puuid", nullable = false, length = 128)
    private String puuid;

    @Column(name = "queue_type", nullable = false, length = 32)
    private String queueType;

    @Column(name = "tier", nullable = false, length = 32)
    private String tier;

    @Column(name = "aggregation_revision", nullable = false, length = 16)
    private String aggregationRevision;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected StatsAggregationCompletion() {
    }

    public Long getId() {
        return id;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getPuuid() {
        return puuid;
    }

    public String getQueueType() {
        return queueType;
    }

    public String getTier() {
        return tier;
    }

    public String getAggregationRevision() {
        return aggregationRevision;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
