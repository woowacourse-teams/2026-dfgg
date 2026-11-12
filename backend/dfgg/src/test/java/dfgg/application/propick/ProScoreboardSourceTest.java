package dfgg.application.propick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import dfgg.infrastructure.external.client.CargoQuery;
import dfgg.infrastructure.external.client.LeaguepediaClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProScoreboardSourceTest {

    private static final LocalDate SINCE = LocalDate.of(2026, 9, 22);

    @Mock
    private LeaguepediaClient leaguepediaClient;

    @InjectMocks
    private ProScoreboardSource source;

    @Test
    @DisplayName("리그 접두사와 시작일로 ScoreboardPlayers를 최근 경기 순으로 요청한다")
    void fetchLeague_WhenPrefixGiven_QueryScoreboardByPrefixAndDate() {
        // given
        given(leaguepediaClient.cargoQuery(any())).willReturn(List.of());
        ArgumentCaptor<CargoQuery> query = ArgumentCaptor.forClass(CargoQuery.class);

        // when
        source.fetchLeague("LCK/", SINCE);

        // then
        verify(leaguepediaClient).cargoQuery(query.capture());
        assertThat(query.getValue().tables()).isEqualTo("ScoreboardPlayers=SP");
        assertThat(query.getValue().where())
                .isEqualTo("SP.OverviewPage LIKE 'LCK/%' AND SP.DateTime_UTC >= '2026-09-22'");
        assertThat(query.getValue().orderBy()).isEqualTo("SP.DateTime_UTC DESC");
        assertThat(query.getValue().fields()).contains(
                "SP.GameId", "SP.OverviewPage", "SP.DateTime_UTC", "SP.Team", "SP.Side", "SP.PlayerWin", "SP.Link",
                "SP.Role", "SP.Champion", "SP.SummonerSpells", "SP.Items", "SP.Trinket", "SP.Runes");
    }

    @Test
    @DisplayName("응답에 접두사로 시작하지 않는 대회 행이 섞여 오면 뺀다 — Cargo LIKE는 대소문자를 가리지 않는다")
    void fetchLeague_WhenOtherLeagueRowReturned_DropIt() {
        // given
        given(leaguepediaClient.cargoQuery(any())).willReturn(List.of(
                Map.of("OverviewPage", "LCK/2026 Season/Playoffs", "GameId", "lck-game"),
                Map.of("OverviewPage", "lck/2026 Season/Showmatch", "GameId", "showmatch")));

        // when
        List<Map<String, String>> rows = source.fetchLeague("LCK/", SINCE);

        // then
        assertThat(rows).extracting(row -> row.get("GameId")).containsExactly("lck-game");
    }
}
