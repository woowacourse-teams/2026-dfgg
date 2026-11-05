package dfgg.domain.aram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 한 패치에서 챔피언이 증강을 가진 판 수와 픽률. 증강 승률은 표시할 수 없어 값을 두지 않는다. */
@Entity
@Table(name = "aram_mayhem_champion_augments")
public class ChampionAugment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String patch;

    @Column(name = "champion_id", nullable = false)
    private int championId;

    @Column(name = "augment_id", nullable = false)
    private int augmentId;

    @Column(nullable = false)
    private long games;

    @Column(name = "pick_rate", nullable = false)
    private double pickRate;

    protected ChampionAugment() {
    }

    public ChampionAugment(String patch, int championId, int augmentId, long games, double pickRate) {
        this.patch = patch;
        this.championId = championId;
        this.augmentId = augmentId;
        this.games = games;
        this.pickRate = pickRate;
    }

    public Long id() {
        return id;
    }

    public double pickRate() {
        return pickRate;
    }
}
