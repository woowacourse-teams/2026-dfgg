package dfgg.domain.match;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RawMatchRepository extends JpaRepository<RawMatch, String> {

    @Query("""
            SELECT rawMatch.matchId
            FROM RawMatch rawMatch
            WHERE rawMatch.matchId > :cursor
              AND NOT EXISTS (
                  SELECT timeline.matchId
                  FROM RawMatchTimeline timeline
                  WHERE timeline.matchId = rawMatch.matchId
              )
            ORDER BY rawMatch.matchId
            """)
    List<String> findMatchIdsMissingTimelineAfter(
            @Param("cursor") String cursor,
            Pageable pageable
    );

    @Query("""
            SELECT rawMatch.matchId
            FROM RawMatch rawMatch
            WHERE rawMatch.matchId > :cursor
              AND EXISTS (
                  SELECT timeline.matchId
                  FROM RawMatchTimeline timeline
                  WHERE timeline.matchId = rawMatch.matchId
              )
              AND (
                  NOT EXISTS (
                      SELECT participant.matchId
                      FROM NormalizedMatchParticipant participant
                      WHERE participant.matchId = rawMatch.matchId
                  )
                  OR EXISTS (
                      SELECT legacyParticipant.matchId
                      FROM NormalizedMatchParticipant legacyParticipant
                      WHERE legacyParticipant.matchId = rawMatch.matchId
                        AND (legacyParticipant.tier IS NULL OR legacyParticipant.tier = '')
                  )
              )
            ORDER BY rawMatch.matchId
            """)
    List<String> findMatchIdsReadyForNormalizationAfter(
            @Param("cursor") String cursor,
            Pageable pageable
    );

    /**
     * <b>이미 정규화된</b> 매치를 재정규화 대상으로 고른다.
     * {@link #findMatchIdsReadyForNormalizationAfter}와 정확히 반대 조건이다 — 그쪽은 아직
     * 정규화되지 않은 매치를 찾고, 이쪽은 이미 된 매치를 다시 돌리기 위해 찾는다.
     *
     * <p>정규화 로직(예: {@code CoreItemPurchaseOrderCalculator})을 고쳤을 때 기존 데이터에
     * 반영하는 유일한 경로다. 정규화 결과는 한 번 저장되면 스스로 갱신되지 않는다.
     *
     * <p>Timeline이 없는 매치는 제외한다 — 원본이 불완전하면 다시 돌려도 같은 결과다.
     * 티어로 거르는 이유는 replay가 티어별로 참가자를 찾기 때문이다.
     */
    @Query("""
            SELECT rawMatch.matchId
            FROM RawMatch rawMatch
            WHERE rawMatch.matchId > :cursor
              AND EXISTS (
                  SELECT timeline.matchId
                  FROM RawMatchTimeline timeline
                  WHERE timeline.matchId = rawMatch.matchId
              )
              AND EXISTS (
                  SELECT participant.matchId
                  FROM NormalizedMatchParticipant participant
                  WHERE participant.matchId = rawMatch.matchId
                    AND participant.tier = :tier
              )
            ORDER BY rawMatch.matchId
            """)
    List<String> findNormalizedMatchIdsForRenormalizationAfter(
            @Param("cursor") String cursor,
            @Param("tier") String tier,
            Pageable pageable
    );

    @Query("""
            SELECT rawMatch.matchId
            FROM RawMatch rawMatch
            WHERE rawMatch.matchId IN :matchIds
            """)
    Set<String> findExistingMatchIds(@Param("matchIds") Collection<String> matchIds);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO raw_matches (match_id, raw_data)
            VALUES (:matchId, :rawData)
            ON CONFLICT (match_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("matchId") String matchId,
            @Param("rawData") String rawData
    );
}
