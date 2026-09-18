package dfgg.domain.match;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RawMatchTimelineRepository extends JpaRepository<RawMatchTimeline, String> {

    @Query("""
            SELECT timeline.matchId
            FROM RawMatchTimeline timeline
            WHERE timeline.matchId IN :matchIds
            """)
    Set<String> findExistingMatchIds(@Param("matchIds") Collection<String> matchIds);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO raw_match_timelines (match_id, raw_data)
            VALUES (:matchId, :rawData)
            ON CONFLICT (match_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("matchId") String matchId,
            @Param("rawData") String rawData
    );
}
