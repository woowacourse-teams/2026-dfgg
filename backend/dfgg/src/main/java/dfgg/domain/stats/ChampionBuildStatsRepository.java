package dfgg.domain.stats;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionBuildStatsRepository extends JpaRepository<ChampionBuildStats, Long> {

    Optional<ChampionBuildStats> findByStatsKey(String statsKey);

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

    @Query(value = """
            SELECT * FROM composition_stats b
            WHERE b.patch = :patch
              AND b.queue_id = :queueId
              AND b.tier = :tier
              AND b.champion_id = :championId
              AND b.position = :position
              AND COALESCE(b.game_count, 0) > 0
              AND (b.enemy_tank_heavy IS NULL OR b.enemy_tank_heavy = :enemyTankHeavy)
              AND (b.enemy_ap_heavy IS NULL OR b.enemy_ap_heavy = :enemyApHeavy)
              AND (b.enemy_assassin_heavy IS NULL OR b.enemy_assassin_heavy = :enemyAssassinHeavy)
              AND (b.ally_has_marksman IS NULL OR b.ally_has_marksman = :allyHasMarksman)
              AND (b.ally_tank_heavy IS NULL OR b.ally_tank_heavy = :allyTankHeavy)
            ORDER BY
              (CASE WHEN b.enemy_tank_heavy IS NOT NULL THEN 1 ELSE 0 END +
               CASE WHEN b.enemy_ap_heavy IS NOT NULL THEN 1 ELSE 0 END +
               CASE WHEN b.enemy_assassin_heavy IS NOT NULL THEN 1 ELSE 0 END +
               CASE WHEN b.ally_has_marksman IS NOT NULL THEN 1 ELSE 0 END +
               CASE WHEN b.ally_tank_heavy IS NOT NULL THEN 1 ELSE 0 END) DESC,
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

    @Query(value = """
            SELECT * FROM composition_stats b
            WHERE b.champion_id = :championId
              AND b.position = :position
              AND COALESCE(b.game_count, 0) > 0
              AND (b.enemy_tank_heavy IS NULL OR b.enemy_tank_heavy = :enemyTankHeavy)
              AND (b.enemy_ap_heavy IS NULL OR b.enemy_ap_heavy = :enemyApHeavy)
              AND (b.enemy_assassin_heavy IS NULL OR b.enemy_assassin_heavy = :enemyAssassinHeavy)
              AND (b.ally_has_marksman IS NULL OR b.ally_has_marksman = :allyHasMarksman)
              AND (b.ally_tank_heavy IS NULL OR b.ally_tank_heavy = :allyTankHeavy)
            ORDER BY
              (CASE WHEN b.enemy_tank_heavy IS NOT NULL THEN 1 ELSE 0 END +
               CASE WHEN b.enemy_ap_heavy IS NOT NULL THEN 1 ELSE 0 END +
               CASE WHEN b.enemy_assassin_heavy IS NOT NULL THEN 1 ELSE 0 END +
               CASE WHEN b.ally_has_marksman IS NOT NULL THEN 1 ELSE 0 END +
               CASE WHEN b.ally_tank_heavy IS NOT NULL THEN 1 ELSE 0 END) DESC,
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
}
