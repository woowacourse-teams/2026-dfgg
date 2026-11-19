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

    /** 픽률은 챔피언 판 수 중 게임 종료 시 증강을 가진 판 수의 비율(%)이다. 비율이 될 수 없는 값은 받지 않는다. */
    public static ChampionAugment fromGames(String patch, int championId, int augmentId, long games,
                                            long championGames) {
        if (championGames <= 0 || games < 0 || games > championGames) {
            throw new IllegalArgumentException(
                    "판 수가 올바르지 않습니다: games=" + games + ", championGames=" + championGames);
        }
        return new ChampionAugment(patch, championId, augmentId, games, games * 100.0 / championGames);
    }

    public Long id() {
        return id;
    }

    public int augmentId() {
        return augmentId;
    }

    public double pickRate() {
        return pickRate;
    }
}
