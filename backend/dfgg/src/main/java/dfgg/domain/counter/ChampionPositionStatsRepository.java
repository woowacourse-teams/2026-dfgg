package dfgg.domain.counter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionPositionStatsRepository extends JpaRepository<ChampionPositionStats, Long> {

    /**
     * 주어진 패치의 챔피언×포지션 등장 수를 저장하고, 저장한 행 수를 돌려준다.
     * 수집 데이터의 MIDDLE·UTILITY는 MID·SUPPORT로 맞추고, 포지션이 없는 참가자는 뺀다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO champion_position_stats (patch, champion_id, position, games)
            SELECT :patch, champion_id, normalized_position, count(*)
            FROM (
                SELECT champion_id,
                       CASE position
                           WHEN 'MIDDLE' THEN 'MID'
                           WHEN 'UTILITY' THEN 'SUPPORT'
                           ELSE position
                       END AS normalized_position
                FROM normalized_match_participants
                WHERE patch = :patch
                  AND position IN ('TOP', 'JUNGLE', 'MID', 'MIDDLE', 'BOTTOM', 'SUPPORT', 'UTILITY')
            ) participant
            GROUP BY champion_id, normalized_position
            """, nativeQuery = true)
    int aggregateFrom(@Param("patch") String patch);
}
