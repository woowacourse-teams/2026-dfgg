package dfgg.domain.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompositionStatsSampleRepository extends JpaRepository<CompositionStatsSample, Long> {

    @Modifying
    @Query(value = """
            WITH inserted_sample AS (
                INSERT INTO composition_stats_samples (composition_stats_id, match_id, puuid)
                SELECT stats.id, :matchId, :puuid
                FROM composition_stats stats
                WHERE stats.stats_key = :statsKey
                ON CONFLICT (composition_stats_id, match_id, puuid) DO NOTHING
                RETURNING composition_stats_id
            )
            UPDATE composition_stats stats
            SET game_count = COALESCE(stats.game_count, 0) + 1,
                win_count = COALESCE(stats.win_count, 0) + CASE WHEN :win THEN 1 ELSE 0 END
            FROM inserted_sample sample
            WHERE stats.id = sample.composition_stats_id
            """, nativeQuery = true)
    int insertAndIncrementIfAbsent(
            @Param("statsKey") String statsKey,
            @Param("matchId") String matchId,
            @Param("puuid") String puuid,
            @Param("win") boolean win
    );
}
