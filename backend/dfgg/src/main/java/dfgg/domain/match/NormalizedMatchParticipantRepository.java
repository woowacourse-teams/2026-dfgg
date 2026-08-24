package dfgg.domain.match;

import java.util.List;
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
}
