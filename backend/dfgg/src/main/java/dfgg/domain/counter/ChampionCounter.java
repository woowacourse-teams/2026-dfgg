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

/**
 * 사용자가 미리 넣어 둔 (적 챔피언, 라인)의 카운터 한 개. 통계로 만들지 않고 패치와 무관하다.
 */
@Entity
@Table(name = "champion_counters")
public class ChampionCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enemy_champion_id", nullable = false)
    private Integer enemyChampionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 16)
    private ChampionPosition position;

    @Column(name = "counter_champion_id", nullable = false)
    private Integer counterChampionId;

    @Column(name = "rank", nullable = false)
    private int rank;

    protected ChampionCounter() {
    }

    public Integer getEnemyChampionId() {
        return enemyChampionId;
    }

    public ChampionPosition getPosition() {
        return position;
    }

    public Integer getCounterChampionId() {
        return counterChampionId;
    }

    public int getRank() {
        return rank;
    }
}
