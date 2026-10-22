package dfgg.domain.propick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Leaguepedia 선수 한 명. {@code leaguepediaPage}로 동명이인을 가른다.
 */
@Entity
@Table(name = "pro_players")
public class ProPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leaguepedia_page", nullable = false, unique = true)
    private String leaguepediaPage;

    @Column(nullable = false)
    private String name;

    private String team;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProPlayer() {
    }

    public Long getId() {
        return id;
    }

    public String getLeaguepediaPage() {
        return leaguepediaPage;
    }

    public String getName() {
        return name;
    }

    public String getTeam() {
        return team;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
