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

    /**
     * 커서 이후의 미완료 정규화 참가자를 매치와 PUUID 순으로 조회한다.
     */
    @Query(value = """
            SELECT participant.match_id AS "matchId", participant.puuid AS "puuid"
            FROM normalized_match_participants participant
            LEFT JOIN stats_aggregation_completions completion
              ON completion.match_id = participant.match_id
             AND completion.puuid = participant.puuid
             AND completion.queue_type = :queueType
             AND completion.tier = participant.tier
             AND completion.aggregation_revision = :aggregationRevision
            WHERE participant.tier = :tier
              AND completion.id IS NULL
              AND (
                    participant.match_id > :afterMatchId
                 OR (participant.match_id = :afterMatchId AND participant.puuid > :afterPuuid)
              )
            ORDER BY participant.match_id, participant.puuid
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

    /**
     * 배치 마지막 매치에 남은 미완료 참가자를 조회해 한 매치가 배치에서 나뉘지 않게 한다.
     */
    @Query(value = """
            SELECT participant.match_id AS "matchId", participant.puuid AS "puuid"
            FROM normalized_match_participants participant
            LEFT JOIN stats_aggregation_completions completion
              ON completion.match_id = participant.match_id
             AND completion.puuid = participant.puuid
             AND completion.queue_type = :queueType
             AND completion.tier = participant.tier
             AND completion.aggregation_revision = :aggregationRevision
            WHERE participant.match_id = :matchId
              AND participant.tier = :tier
              AND participant.puuid > :afterPuuid
              AND completion.id IS NULL
            ORDER BY participant.puuid
            """, nativeQuery = true)
    List<PendingTarget> findRemainingTargetsForMatch(
            @Param("matchId") String matchId,
            @Param("queueType") String queueType,
            @Param("tier") String tier,
            @Param("aggregationRevision") String aggregationRevision,
            @Param("afterPuuid") String afterPuuid
    );

    /**
     * 통계 집계 시작 전에 참가자 completion을 선점하고, 이미 존재하면 아무 작업도 하지 않는다.
     */
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

    /**
     * 재집계가 끝난 참가자의 completion 시각을 새로 기록한다.
     */
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
