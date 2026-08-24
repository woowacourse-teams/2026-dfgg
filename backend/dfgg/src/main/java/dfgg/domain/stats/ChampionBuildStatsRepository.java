package dfgg.domain.stats;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionBuildStatsRepository extends JpaRepository<ChampionBuildStats, Long> {

    /**
     * 통계 식별자로 하나의 빌드 통계를 조회한다.
     */
    Optional<ChampionBuildStats> findByStatsKey(String statsKey);

    /**
     * 동일한 통계 조건이 없을 때만 통계 기본 행을 만든다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO composition_stats (
                patch,
                queue_id,
                champion_id,
                position,
                enemy_tank_heavy,
                enemy_ap_heavy,
                enemy_assassin_heavy,
                ally_has_marksman,
                ally_tank_heavy,
                tier,
                build_key,
                stats_key,
                win_count,
                game_count
            )
            VALUES (
                :patch,
                :queueId,
                :championId,
                :position,
                :enemyTankHeavy,
                :enemyApHeavy,
                :enemyAssassinHeavy,
                :allyHasMarksman,
                :allyTankHeavy,
                :tier,
                :buildKey,
                :statsKey,
                0,
                0
            )
            ON CONFLICT (stats_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("patch") String patch,
            @Param("queueId") Integer queueId,
            @Param("championId") Long championId,
            @Param("position") String position,
            @Param("enemyTankHeavy") Boolean enemyTankHeavy,
            @Param("enemyApHeavy") Boolean enemyApHeavy,
            @Param("enemyAssassinHeavy") Boolean enemyAssassinHeavy,
            @Param("allyHasMarksman") Boolean allyHasMarksman,
            @Param("allyTankHeavy") Boolean allyTankHeavy,
            @Param("tier") String tier,
            @Param("buildKey") String buildKey,
            @Param("statsKey") String statsKey
    );

    /**
     * 통계 행에 아이템과 구매 순서를 연결한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO composition_stats_items (
                composition_stats_id,
                item_id,
                item_order
            )
            SELECT stats.id, :itemId, :itemOrder
            FROM composition_stats stats
            WHERE stats.stats_key = :statsKey
            """, nativeQuery = true)
    int insertItem(
            @Param("statsKey") String statsKey,
            @Param("itemId") Long itemId,
            @Param("itemOrder") int itemOrder
    );

    /**
     * 패치·큐·티어·챔피언·포지션과 조합 조건에 맞는 가장 적합한 통계를 조회한다.
     */
    @Query(value = """
            SELECT * FROM composition_stats b
            WHERE b.patch = :patch
              AND b.queue_id = :queueId
              AND b.tier = :tier
              AND b.champion_id = :championId
              AND b.position = :position
              AND COALESCE(b.game_count, 0) > 0
              AND b.enemy_tank_heavy = :enemyTankHeavy
              AND b.enemy_ap_heavy = :enemyApHeavy
              AND b.enemy_assassin_heavy = :enemyAssassinHeavy
              AND b.ally_has_marksman = :allyHasMarksman
              AND b.ally_tank_heavy = :allyTankHeavy
            ORDER BY
              b.game_count DESC,
              b.win_count DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ChampionBuildStats> findBestMatchingStatsForScope(
            @Param("patch") String patch,
            @Param("queueId") Integer queueId,
            @Param("tier") String tier,
            @Param("championId") Long championId,
            @Param("position") String position,
            @Param("enemyTankHeavy") boolean enemyTankHeavy,
            @Param("enemyApHeavy") boolean enemyApHeavy,
            @Param("enemyAssassinHeavy") boolean enemyAssassinHeavy,
            @Param("allyHasMarksman") boolean allyHasMarksman,
            @Param("allyTankHeavy") boolean allyTankHeavy
    );

    /**
     * 패치와 티어를 제한하지 않고 챔피언·포지션·조합 조건에 맞는 대표 통계를 조회한다.
     */
    @Query(value = """
            SELECT * FROM composition_stats b
            WHERE b.champion_id = :championId
              AND b.position = :position
              AND COALESCE(b.game_count, 0) > 0
              AND b.enemy_tank_heavy = :enemyTankHeavy
              AND b.enemy_ap_heavy = :enemyApHeavy
              AND b.enemy_assassin_heavy = :enemyAssassinHeavy
              AND b.ally_has_marksman = :allyHasMarksman
              AND b.ally_tank_heavy = :allyTankHeavy
            ORDER BY
              b.game_count DESC,
              b.win_count DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ChampionBuildStats> findBestMatchingStats(
            @Param("championId") Long championId,
            @Param("position") String position,
            @Param("enemyTankHeavy") boolean enemyTankHeavy,
            @Param("enemyApHeavy") boolean enemyApHeavy,
            @Param("enemyAssassinHeavy") boolean enemyAssassinHeavy,
            @Param("allyHasMarksman") boolean allyHasMarksman,
            @Param("allyTankHeavy") boolean allyTankHeavy
    );

    /**
     * 챔피언·포지션·조합 조건에 맞는 모든 빌드 통계를 빌드 키 순으로 조회한다.
     */
    @Query(value = """
            SELECT * FROM composition_stats b
            WHERE b.champion_id = :championId
              AND b.position = :position
              AND COALESCE(b.game_count, 0) > 0
              AND b.enemy_tank_heavy = :enemyTankHeavy
              AND b.enemy_ap_heavy = :enemyApHeavy
              AND b.enemy_assassin_heavy = :enemyAssassinHeavy
              AND b.ally_has_marksman = :allyHasMarksman
              AND b.ally_tank_heavy = :allyTankHeavy
            ORDER BY b.build_key
            """, nativeQuery = true)
    List<ChampionBuildStats> findAllMatchingStats(
            @Param("championId") Long championId,
            @Param("position") String position,
            @Param("enemyTankHeavy") boolean enemyTankHeavy,
            @Param("enemyApHeavy") boolean enemyApHeavy,
            @Param("enemyAssassinHeavy") boolean enemyAssassinHeavy,
            @Param("allyHasMarksman") boolean allyHasMarksman,
            @Param("allyTankHeavy") boolean allyTankHeavy
    );
}
