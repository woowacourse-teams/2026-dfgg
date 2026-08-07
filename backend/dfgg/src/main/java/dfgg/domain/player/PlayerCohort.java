package dfgg.domain.player;

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
 * The latest collection target for a player. A copy of this value is attached
 * to every match when the match is collected.
 */
@Entity
@Table(
        name = "player_cohorts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_player_cohort",
                columnNames = {"puuid", "queue_type"}
        )
)
public class PlayerCohort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    protected PlayerCohort() {
    }

    public PlayerCohort(
            String puuid,
            String queueType,
            String tier,
            String division,
            Instant collectedAt
    ) {
        Assert.hasText(puuid, "puuid must not be blank");
        Assert.hasText(queueType, "queueType must not be blank");
        Assert.hasText(tier, "tier must not be blank");
        Assert.hasText(division, "division must not be blank");
        this.puuid = puuid;
        this.queueType = queueType;
        this.tier = tier;
        this.division = division;
        this.collectedAt = Objects.requireNonNull(collectedAt, "collectedAt must not be null");
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
