package dfgg.domain.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompositionStatsSampleRepository extends JpaRepository<CompositionStatsSample, Long> {

    /**
     * 같은 통계·매치·참가자 표본이 처음 들어온 경우에만 표본을 만들고 통계 수를 증가시킨다.
     */
    @Modifying
    @Query(value = """
            WITH inserted_sample AS (
                INSERT INTO composition_stats_samples (composition_stats_id, match_id, puuid, win)
                SELECT stats.id, :matchId, :puuid, :win
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

    /**
     * 한 매치와 참가자가 기록된 표본 개수를 센다.
     */
    long countByMatchIdAndPuuid(String matchId, String puuid);

    /**
     * 승패가 아직 채워지지 않은 표본 개수를 센다.
     */
    long countByMatchIdAndPuuidAndWinIsNull(String matchId, String puuid);

    /**
     * 저장된 정규화 참가자 정보로 누락된 표본 승패를 보완한다.
     */
    @Modifying
    @Query(value = """
            UPDATE composition_stats_samples sample
            SET win = participant.win
            FROM normalized_match_participants participant
            WHERE sample.match_id = :matchId
              AND sample.puuid = :puuid
              AND sample.win IS NULL
              AND participant.match_id = sample.match_id
              AND participant.puuid = sample.puuid
            """, nativeQuery = true)
    int backfillMissingWinFromNormalized(
            @Param("matchId") String matchId,
            @Param("puuid") String puuid
    );

    /**
     * 참가자의 기존 표본을 삭제하고 각 통계의 게임 수와 승리 수를 함께 감소시킨다.
     */
    @Modifying
    @Query(value = """
            WITH deleted_sample AS (
                DELETE FROM composition_stats_samples
                WHERE match_id = :matchId
                  AND puuid = :puuid
                RETURNING composition_stats_id, win
            ), contribution AS (
                SELECT composition_stats_id,
                       COUNT(*)::integer AS game_count,
                       (COUNT(*) FILTER (WHERE win))::integer AS win_count
                FROM deleted_sample
                GROUP BY composition_stats_id
            )
            UPDATE composition_stats stats
            SET game_count = COALESCE(stats.game_count, 0) - contribution.game_count,
                win_count = COALESCE(stats.win_count, 0) - contribution.win_count
            FROM contribution
            WHERE stats.id = contribution.composition_stats_id
              AND COALESCE(stats.game_count, 0) >= contribution.game_count
              AND COALESCE(stats.win_count, 0) >= contribution.win_count
            """, nativeQuery = true)
    int deleteContributionsAndDecrement(
            @Param("matchId") String matchId,
            @Param("puuid") String puuid
    );
}
