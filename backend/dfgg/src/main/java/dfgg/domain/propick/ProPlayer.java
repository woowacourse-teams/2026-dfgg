package dfgg.domain.propick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Locale;

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

    public ProPlayer(String leaguepediaPage, String name, String team, String imageUrl, OffsetDateTime updatedAt) {
        this.leaguepediaPage = leaguepediaPage;
        this.imageUrl = imageUrl;
        update(name, team, imageUrl, updatedAt);
    }

    /** 사진을 이번에 못 찾았으면 전에 찾은 사진을 남긴다. */
    public void update(String name, String team, String imageUrl, OffsetDateTime updatedAt) {
        this.name = name;
        this.team = team;
        this.updatedAt = updatedAt;
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
    }

    /** 위키 페이지 이름은 첫 글자가 대문자다. 경기 기록의 링크는 소문자로 올 수 있어(kyeahoo) 맞춘다. */
    public static String canonicalPage(String page) {
        String stripped = page.strip();
        if (stripped.isEmpty()) {
            return stripped;
        }
        return stripped.substring(0, 1).toUpperCase(Locale.ROOT) + stripped.substring(1);
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
