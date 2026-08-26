package dfgg.domain.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedMatchParticipantRepository extends JpaRepository<NormalizedMatchParticipant, Long> {

    List<NormalizedMatchParticipant> findByMatchId(String matchId);

    boolean existsByMatchId(String matchId);

    @Query("""
            SELECT participant.puuid
            FROM NormalizedMatchParticipant participant
            WHERE participant.matchId = :matchId
              AND participant.tier = :tier
            """)
    List<String> findPuuidsByMatchIdAndTier(
            @Param("matchId") String matchId,
            @Param("tier") String tier
    );

    void deleteByMatchId(String matchId);

    List<NormalizedMatchParticipant> findByMatchIdIn(Collection<String> matchIds);

    @Query("""
            SELECT DISTINCT p.matchId
            FROM NormalizedMatchParticipant p
            ORDER BY p.matchId
            """)
    Slice<String> findDistinctMatchIds(Pageable pageable);

    @Query(value = """
            SELECT item_id, count(*)
            FROM (
                SELECT unnest(string_to_array(final_core_item_ids, ',')) AS item_id
                FROM normalized_match_participants
            ) AS item_tokens
            WHERE item_id <> ''
            GROUP BY item_id
            """, nativeQuery = true)
    List<Object[]> countItemOccurrences();

    /**
     * 챔피언·포지션에서 가장 많이 등장한 코어 아이템 빌드를 콤마 구분 문자열로 반환한다.
     *
     * <p>{@code position}에는 Riot 원시값이 저장되므로(정규화는 마이닝 시점에만 일어남)
     * 호출자가 {@code ChampionPositionNormalizer.riotValuesOf(...)}로 별칭까지 넘겨야 한다.
     */
    @Query(value = """
            SELECT final_core_item_ids
            FROM normalized_match_participants
            WHERE champion_id = :championId
              AND position IN (:positions)
              AND final_core_item_ids <> ''
            GROUP BY final_core_item_ids
            ORDER BY count(*) DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findMostFrequentBuild(
            @Param("championId") Long championId,
            @Param("positions") Collection<String> positions
    );
}
