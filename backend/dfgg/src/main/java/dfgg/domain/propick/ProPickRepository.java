package dfgg.domain.propick;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProPickRepository extends JpaRepository<ProPick, Long> {

    /** (대회, 포지션) 픽을 최근 경기 순으로 읽는다. 선수·대회는 함께, 주문·아이템·룬은 컬렉션별 한 번씩 읽는다. */
    @Query("""
            SELECT pick FROM ProPick pick
            JOIN FETCH pick.player
            JOIN FETCH pick.tournament
            WHERE pick.tournament.id = :tournamentId AND pick.position = :position
            ORDER BY pick.playedAt DESC, pick.id DESC
            """)
    List<ProPick> findByTournamentAndPosition(@Param("tournamentId") Long tournamentId,
                                              @Param("position") ChampionPosition position);
}
