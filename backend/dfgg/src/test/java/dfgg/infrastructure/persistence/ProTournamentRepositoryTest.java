package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.propick.ProTournament;
import dfgg.domain.propick.ProTournamentRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/pro-tournament-latest-test-data.sql")
class ProTournamentRepositoryTest {

    @Autowired
    private ProTournamentRepository tournamentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("마지막 경기가 가장 늦은 대회를 최근 대회로 본다 — 시작일이 늦거나 경기가 없는 대회가 아니다")
    void findLatestPlayed_WhenPicksExist_ReturnTournamentOfLatestPick() {
        // when
        Optional<ProTournament> latest = tournamentRepository.findLatestPlayed();

        // then
        assertThat(latest).hasValueSatisfying(tournament -> {
            assertThat(tournament.getId()).isEqualTo(2L);
            assertThat(tournament.getOverviewPage()).isEqualTo("LCK/2026 Season/Playoffs");
            assertThat(tournament.getName()).isEqualTo("LCK 2026 Playoffs");
            assertThat(tournament.getLeague()).isEqualTo("LCK");
            assertThat(tournament.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        });
    }

    @Test
    @DisplayName("픽이 하나도 없으면 비어 있다")
    void findLatestPlayed_WhenNoPick_ReturnEmpty() {
        // given
        jdbcTemplate.update("DELETE FROM pro_picks");

        // when
        Optional<ProTournament> latest = tournamentRepository.findLatestPlayed();

        // then
        assertThat(latest).isEmpty();
    }
}
