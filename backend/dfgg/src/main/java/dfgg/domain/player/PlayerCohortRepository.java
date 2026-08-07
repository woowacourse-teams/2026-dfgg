package dfgg.domain.player;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerCohortRepository extends JpaRepository<PlayerCohort, Long> {

    interface Target {
        String getPuuid();

        String getQueueType();

        String getTier();

        String getDivision();

        Instant getCollectedAt();
    }

    @Query("""
            SELECT cohort
            FROM PlayerCohort cohort
            WHERE cohort.puuid IN :puuids
              AND cohort.queueType = :queueType
            """)
    List<Target> findTargetsByPuuidsAndQueueType(
            @Param("puuids") Collection<String> puuids,
            @Param("queueType") String queueType
    );

    @Modifying
    @Query(value = """
            INSERT INTO player_cohorts (
                puuid, queue_type, tier, division, collected_at
            )
            VALUES (
                :puuid, :queueType, :tier, :division, :collectedAt
            )
            ON CONFLICT (puuid, queue_type) DO UPDATE SET
                tier = EXCLUDED.tier,
                division = EXCLUDED.division,
                collected_at = EXCLUDED.collected_at
            """, nativeQuery = true)
    int upsert(
            @Param("puuid") String puuid,
            @Param("queueType") String queueType,
            @Param("tier") String tier,
            @Param("division") String division,
            @Param("collectedAt") Instant collectedAt
    );
}
