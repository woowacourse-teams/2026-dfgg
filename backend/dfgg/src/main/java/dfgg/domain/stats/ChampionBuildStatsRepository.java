package dfgg.domain.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChampionBuildStatsRepository extends JpaRepository<ChampionBuildStats, Long> {

    Optional<ChampionBuildStats> findByStatsKey(String statsKey);

    @Query(value = """
            SELECT * FROM composition_stats b
            WHERE b.patch = :patch
              AND b.queue_id = :queueId
              AND b.tier = :tier
              AND b.champion_id = :championId
              AND b.position = :position
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
