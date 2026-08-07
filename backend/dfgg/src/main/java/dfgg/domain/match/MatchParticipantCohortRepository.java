package dfgg.domain.match;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantCohortRepository extends JpaRepository<MatchParticipantCohort, Long> {

    @Query("""
            SELECT cohort.puuid
            FROM MatchParticipantCohort cohort
            WHERE cohort.matchId = :matchId
              AND cohort.queueType = :queueType
              AND cohort.tier = :tier
            """)
    List<String> findPuuidsByMatchIdAndQueueTypeAndTier(
            @Param("matchId") String matchId,
            @Param("queueType") String queueType,
            @Param("tier") String tier
    );

    @Modifying
    @Query(value = """
            INSERT INTO match_participant_cohorts (
                match_id, puuid, queue_type, tier, division, collected_at
            )
            VALUES (
                :matchId, :puuid, :queueType, :tier, :division, :collectedAt
            )
            ON CONFLICT (match_id, puuid, queue_type) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("matchId") String matchId,
            @Param("puuid") String puuid,
            @Param("queueType") String queueType,
            @Param("tier") String tier,
            @Param("division") String division,
            @Param("collectedAt") java.time.Instant collectedAt
    );

    @Query("""
            SELECT cohort
            FROM MatchParticipantCohort cohort
            WHERE cohort.matchId IN :matchIds
            """)
    List<MatchParticipantCohort> findByMatchIdIn(@Param("matchIds") Collection<String> matchIds);
}
