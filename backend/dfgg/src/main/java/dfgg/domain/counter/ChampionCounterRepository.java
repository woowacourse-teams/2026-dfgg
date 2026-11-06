package dfgg.domain.counter;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChampionCounterRepository extends JpaRepository<ChampionCounter, Long> {

    /** (적, 라인)의 카운터를 저장 순위대로. 데이터가 없으면 빈 목록이다. */
    List<ChampionCounter> findByEnemyChampionIdAndPositionOrderByRankAsc(
            Integer enemyChampionId, ChampionPosition position);
}
