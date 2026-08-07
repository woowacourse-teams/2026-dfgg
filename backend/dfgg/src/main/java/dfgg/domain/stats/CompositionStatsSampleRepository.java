package dfgg.domain.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompositionStatsSampleRepository extends JpaRepository<CompositionStatsSample, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO composition_stats_samples (composition_stats_id, match_id, puuid)
            SELECT stats.id, :matchId, :puuid
            FROM composition_stats stats
            WHERE stats.stats_key = :statsKey
            ON CONFLICT (composition_stats_id, match_id, puuid) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("statsKey") String statsKey,
            @Param("matchId") String matchId,
            @Param("puuid") String puuid
    );
}
