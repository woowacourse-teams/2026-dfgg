package dfgg.domain.match;

import java.util.Collection;
import java.util.List;
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
}
