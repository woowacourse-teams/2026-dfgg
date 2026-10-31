package dfgg.domain.counter;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionPositionStatsRepository extends JpaRepository<ChampionPositionStats, Long> {

    /** 집계된 패치 목록. 조회 기준 패치는 이 중 최고 패치다 (참가자 테이블을 스캔하지 않는다). */
    @Query("SELECT DISTINCT stats.patch FROM ChampionPositionStats stats")
    List<String> findDistinctPatches();

    List<ChampionPositionStats> findByPatchAndChampionIdIn(String patch, Collection<Integer> championIds);

    /** 다시 집계할 패치의 행만 지운다. 이전 패치 행은 남긴다. */
    @Modifying
    @Query(value = "DELETE FROM champion_position_stats WHERE patch = :patch", nativeQuery = true)
    int deleteByPatch(@Param("patch") String patch);

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
