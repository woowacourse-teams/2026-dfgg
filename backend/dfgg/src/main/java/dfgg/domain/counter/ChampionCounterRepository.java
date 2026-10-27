package dfgg.domain.counter;

import dfgg.domain.champion.ChampionPosition;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionCounterRepository extends JpaRepository<ChampionCounter, Long> {

    /** 여러 적의 같은 라인 카운터를 한 번에 읽는다. 적마다 저장 순위대로 묶여 있다. */
    @Query("""
            SELECT counter FROM ChampionCounter counter
            WHERE counter.enemyChampionId IN :enemyChampionIds AND counter.position = :position
            ORDER BY counter.enemyChampionId, counter.rank
            """)
    List<ChampionCounter> findByEnemiesAndPosition(@Param("enemyChampionIds") Collection<Integer> enemyChampionIds,
                                                   @Param("position") ChampionPosition position);
}
