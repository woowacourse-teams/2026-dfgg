package dfgg.domain.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.springframework.util.Assert;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @Column(name = "puuid", length = 128)
    private String puuid;

    @Column(name = "platform", nullable = false, length = 16)
    private String platform;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Player() {
    }

    public Player(String puuid, String platform, Instant seenAt) {
        // 이 부분 나중에 검토 예정.
        Assert.hasText(puuid, "puuid must not be blank");
        Assert.hasText(platform, "platform must not be blank");
        Objects.requireNonNull(seenAt, "seenAt must not be null");

        this.puuid = puuid;
        this.platform = platform;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
    }

    public String getPuuid() {
        return puuid;
    }

    public String getPlatform() {
        return platform;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
