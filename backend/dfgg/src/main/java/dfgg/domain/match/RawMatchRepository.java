package dfgg.domain.match;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RawMatchRepository extends JpaRepository<RawMatch, String> {

    @Query("""
            SELECT rawMatch.matchId
            FROM RawMatch rawMatch
            WHERE rawMatch.matchId IN :matchIds
            """)
    Set<String> findExistingMatchIds(@Param("matchIds") Collection<String> matchIds);

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
