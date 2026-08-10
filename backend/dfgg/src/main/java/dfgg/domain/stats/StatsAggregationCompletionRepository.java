package dfgg.domain.stats;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatsAggregationCompletionRepository
        extends JpaRepository<StatsAggregationCompletion, Long> {

    interface PendingTarget {

        String getMatchId();

        String getPuuid();
    }

    @Query(value = """
            SELECT COUNT(DISTINCT cohort.match_id)
            FROM match_participant_cohorts cohort
            JOIN raw_matches raw_match
              ON raw_match.match_id = cohort.match_id
            LEFT JOIN stats_aggregation_completions completion
              ON completion.match_id = cohort.match_id
             AND completion.puuid = cohort.puuid
             AND completion.queue_type = cohort.queue_type
             AND completion.tier = cohort.tier
             AND completion.aggregation_revision = :aggregationRevision
            WHERE cohort.queue_type = :queueType
              AND cohort.tier = :tier
              AND completion.id IS NULL
            """, nativeQuery = true)
    long countPendingMatches(
            @Param("queueType") String queueType,
            @Param("tier") String tier,
            @Param("aggregationRevision") String aggregationRevision
    );

    @Query(value = """
            SELECT cohort.match_id AS "matchId", cohort.puuid AS "puuid"
            FROM match_participant_cohorts cohort
            JOIN raw_matches raw_match
              ON raw_match.match_id = cohort.match_id
            LEFT JOIN stats_aggregation_completions completion
              ON completion.match_id = cohort.match_id
             AND completion.puuid = cohort.puuid
             AND completion.queue_type = cohort.queue_type
             AND completion.tier = cohort.tier
             AND completion.aggregation_revision = :aggregationRevision
            WHERE cohort.queue_type = :queueType
              AND cohort.tier = :tier
              AND completion.id IS NULL
              AND (
                    cohort.match_id > :afterMatchId
                 OR (cohort.match_id = :afterMatchId AND cohort.puuid > :afterPuuid)
              )
            ORDER BY cohort.match_id, cohort.puuid
            LIMIT :batchSize
            """, nativeQuery = true)
    List<PendingTarget> findPendingTargetsAfter(
            @Param("queueType") String queueType,
            @Param("tier") String tier,
            @Param("aggregationRevision") String aggregationRevision,
            @Param("afterMatchId") String afterMatchId,
            @Param("afterPuuid") String afterPuuid,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
            SELECT cohort.match_id AS "matchId", cohort.puuid AS "puuid"
            FROM match_participant_cohorts cohort
            LEFT JOIN stats_aggregation_completions completion
              ON completion.match_id = cohort.match_id
             AND completion.puuid = cohort.puuid
             AND completion.queue_type = cohort.queue_type
             AND completion.tier = cohort.tier
             AND completion.aggregation_revision = :aggregationRevision
            WHERE cohort.match_id = :matchId
              AND cohort.queue_type = :queueType
              AND cohort.tier = :tier
              AND cohort.puuid > :afterPuuid
              AND completion.id IS NULL
            ORDER BY cohort.puuid
            """, nativeQuery = true)
    List<PendingTarget> findRemainingTargetsForMatch(
            @Param("matchId") String matchId,
            @Param("queueType") String queueType,
            @Param("tier") String tier,
            @Param("aggregationRevision") String aggregationRevision,
            @Param("afterPuuid") String afterPuuid
    );

    @Query(value = """
            SELECT true
            FROM pg_advisory_xact_lock(hashtextextended(CAST(:matchId AS text), 0))
            """, nativeQuery = true)
    boolean acquireMatchLock(@Param("matchId") String matchId);

    @Modifying
    @Query(value = """
            INSERT INTO stats_aggregation_completions (
                match_id,
                puuid,
                queue_type,
                tier,
                aggregation_revision,
                completed_at
            )
            VALUES (
                :matchId,
                :puuid,
                :queueType,
                :tier,
                :aggregationRevision,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (match_id, puuid, queue_type, tier, aggregation_revision) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("matchId") String matchId,
            @Param("puuid") String puuid,
            @Param("queueType") String queueType,
            @Param("tier") String tier,
            @Param("aggregationRevision") String aggregationRevision
    );

    @Modifying
    @Query(value = """
            INSERT INTO stats_aggregation_completions (
                match_id,
                puuid,
                queue_type,
                tier,
                aggregation_revision,
                completed_at
            )
            VALUES (
                :matchId,
                :puuid,
                :queueType,
                :tier,
                :aggregationRevision,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (match_id, puuid, queue_type, tier, aggregation_revision)
            DO UPDATE SET completed_at = EXCLUDED.completed_at
            """, nativeQuery = true)
    int markCompleted(
            @Param("matchId") String matchId,
            @Param("puuid") String puuid,
            @Param("queueType") String queueType,
            @Param("tier") String tier,
            @Param("aggregationRevision") String aggregationRevision
    );
}
