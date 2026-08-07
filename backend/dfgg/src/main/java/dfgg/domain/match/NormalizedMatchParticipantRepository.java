package dfgg.domain.match;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizedMatchParticipantRepository extends JpaRepository<NormalizedMatchParticipant, Long> {

    List<NormalizedMatchParticipant> findByMatchId(String matchId);

    void deleteByMatchId(String matchId);
}
