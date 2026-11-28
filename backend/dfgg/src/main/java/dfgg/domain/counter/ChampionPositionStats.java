package dfgg.domain.counter;

import dfgg.domain.champion.ChampionPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 한 패치에서 챔피언이 한 포지션으로 나온 수. 라인 분포 %의 분자다.
 */
@Entity
@Table(
        name = "champion_position_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_champion_position_stats",
                columnNames = {"patch", "champion_id", "position"}
        )
)
public class ChampionPositionStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patch", nullable = false, length = 16)
    private String patch;

    @Column(name = "champion_id", nullable = false)
    private Integer championId;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 16)
    private ChampionPosition position;

    @Column(name = "games", nullable = false)
    private int games;

    protected ChampionPositionStats() {
    }

    public String getPatch() {
        return patch;
    }

    public Integer getChampionId() {
        return championId;
    }

    public ChampionPosition getPosition() {
        return position;
    }

    public int getGames() {
        return games;
    }
}
