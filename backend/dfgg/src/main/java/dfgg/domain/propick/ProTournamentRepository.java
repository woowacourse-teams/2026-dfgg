package dfgg.domain.propick;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProTournamentRepository extends JpaRepository<ProTournament, Long> {

    /** 최근 대회: 픽의 played_at이 가장 늦은 대회. 시작일이 아니라 실제 경기 시각으로 정한다. */
    @Query(value = """
            SELECT tournament.*
            FROM pro_picks pick
            JOIN pro_tournaments tournament ON tournament.id = pick.tournament_id
            ORDER BY pick.played_at DESC, tournament.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ProTournament> findLatestPlayed();
}
