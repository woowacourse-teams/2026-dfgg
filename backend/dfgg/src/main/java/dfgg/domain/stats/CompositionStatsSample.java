package dfgg.domain.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "composition_stats_samples",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_composition_stats_sample",
                columnNames = {"composition_stats_id", "match_id", "puuid"}
        )
)
public class CompositionStatsSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "composition_stats_id", nullable = false)
    private ChampionBuildStats stats;

    @Column(name = "match_id", nullable = false, length = 32)
    private String matchId;

    @Column(name = "puuid", nullable = false, length = 128)
    private String puuid;

    @Column(name = "win")
    private Boolean win;

    protected CompositionStatsSample() {
    }

    public CompositionStatsSample(ChampionBuildStats stats, String matchId, String puuid) {
        this(stats, matchId, puuid, null);
    }

    public CompositionStatsSample(ChampionBuildStats stats, String matchId, String puuid, Boolean win) {
        this.stats = stats;
        this.matchId = matchId;
        this.puuid = puuid;
        this.win = win;
    }

    public Boolean getWin() {
        return win;
    }
}
