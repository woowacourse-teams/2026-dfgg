package dfgg.domain.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import org.springframework.util.Assert;

/**
 * Preserves the tier cohort used when a match was collected.
 */
@Entity
@Table(
        name = "match_participant_cohorts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_participant_cohort",
                columnNames = {"match_id", "puuid", "queue_type"}
        )
)
public class MatchParticipantCohort {

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

    @Column(name = "division", nullable = false, length = 4)
    private String division;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected MatchParticipantCohort() {
    }

    public MatchParticipantCohort(
            String matchId,
            String puuid,
            String queueType,
            String tier,
            String division,
            Instant collectedAt
    ) {
        Assert.hasText(matchId, "matchId must not be blank");
        Assert.hasText(puuid, "puuid must not be blank");
        Assert.hasText(queueType, "queueType must not be blank");
        Assert.hasText(tier, "tier must not be blank");
        Assert.hasText(division, "division must not be blank");
        Objects.requireNonNull(collectedAt, "collectedAt must not be null");
        this.matchId = matchId;
        this.puuid = puuid;
        this.queueType = queueType;
        this.tier = tier;
        this.division = division;
        this.collectedAt = collectedAt;
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

    public String getDivision() {
        return division;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }
}
