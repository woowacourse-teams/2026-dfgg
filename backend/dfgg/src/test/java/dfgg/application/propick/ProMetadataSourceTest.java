package dfgg.application.propick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dfgg.infrastructure.external.client.CargoQuery;
import dfgg.infrastructure.external.client.LeaguepediaClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProMetadataSourceTest {

    @Mock
    private LeaguepediaClient leaguepediaClient;

    @InjectMocks
    private ProMetadataSource source;

    @Test
    @DisplayName("대회 페이지 목록으로 Tournaments를 받아 이름·리그·지역·시작·종료일을 읽는다")
    void tournaments_WhenFound_ReadTournamentFields() {
        // given
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().equals("Tournaments=T"))))
                .willReturn(List.of(Map.of("OverviewPage", "LCK/2026 Season/Playoffs", "Name", "LCK 2026 Playoffs",
                        "League", "LoL Champions Korea", "Region", "Korea", "DateStart", "2026-09-01", "Date", "")));

        // when
        List<ProTournamentInfo> tournaments = source.tournaments(List.of("LCK/2026 Season/Playoffs"));

        // then
        assertThat(tournaments).containsExactly(new ProTournamentInfo("LCK/2026 Season/Playoffs",
                "LCK 2026 Playoffs", "LoL Champions Korea", "Korea", LocalDate.of(2026, 9, 1), null));
    }

    @Test
    @DisplayName("Tournaments에 없는 대회는 페이지 이름과 첫 경로로 채운다")
    void tournaments_WhenNotFound_FallBackToPageName() {
        // given
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().equals("Tournaments=T"))))
                .willReturn(List.of());

        // when
        List<ProTournamentInfo> tournaments = source.tournaments(List.of("LCK/2026 Season/Playoffs"));

        // then
        assertThat(tournaments).containsExactly(new ProTournamentInfo("LCK/2026 Season/Playoffs",
                "LCK/2026 Season/Playoffs", "LCK", null, null, null));
    }

    @Test
    @DisplayName("IN 목록의 작은따옴표는 두 번 써서 넣는다")
    void tournaments_WhenPageHasQuote_EscapeInWhere() {
        // given
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().equals("Tournaments=T"))))
                .willReturn(List.of());
        ArgumentCaptor<CargoQuery> query = ArgumentCaptor.forClass(CargoQuery.class);

        // when
        source.tournaments(List.of("Rift's Cup/2026"));

        // then
        verify(leaguepediaClient).cargoQuery(query.capture());
        assertThat(query.getValue().where()).isEqualTo("T.OverviewPage IN ('Rift''s Cup/2026')");
    }

    @Test
    @DisplayName("대회는 50개씩 나눠 요청한다")
    void tournaments_WhenMoreThanFifty_QueryInBatches() {
        // given
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().equals("Tournaments=T"))))
                .willReturn(List.of());
        List<String> pages = IntStream.range(0, 51).mapToObj(index -> "LCK/Event " + index).toList();

        // when
        source.tournaments(pages);

        // then
        verify(leaguepediaClient, times(2)).cargoQuery(argThat(query -> query != null && query.tables().equals("Tournaments=T")));
    }

    @Test
    @DisplayName("선수는 첫 글자를 대문자로 맞춰 Players에서 ID·팀을 읽고, 가장 최근 대회 프로필 사진 URL을 붙인다")
    void players_WhenFound_ReadProfileAndLatestImage() {
        // given
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().equals("Players=P"))))
                .willReturn(List.of(Map.of("OverviewPage", "Kyeahoo", "ID", "Kyeahoo", "Team", "DRX")));
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().startsWith("PlayerImages"))))
                .willReturn(List.of(
                        Map.of("Link", "Kyeahoo", "FileName", "DRX Kyeahoo 2026 Split 2.png", "DateStart", "2026-07-01"),
                        Map.of("Link", "Kyeahoo", "FileName", "DRX Kyeahoo 2026 Split 1.png", "DateStart", "2026-03-01")));
        given(leaguepediaClient.imageUrls(anyCollection()))
                .willReturn(Map.of("DRX Kyeahoo 2026 Split 2.png", "https://static.wikia.nocookie.net/kyeahoo2.png"));

        // when
        List<ProPlayerInfo> players = source.players(List.of("kyeahoo"));

        // then
        assertThat(players).containsExactly(
                new ProPlayerInfo("Kyeahoo", "Kyeahoo", "DRX", "https://static.wikia.nocookie.net/kyeahoo2.png"));
        verify(leaguepediaClient).imageUrls(List.of("DRX Kyeahoo 2026 Split 2.png"));
    }

    @Test
    @DisplayName("Players·PlayerImages가 소문자로 시작하는 페이지 이름(kyeahoo)을 돌려줘도 같은 선수로 맞춰 사진과 팀을 붙인다")
    void players_WhenWikiReturnsLowercasePage_MatchCanonicalPage() {
        // given
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().equals("Players=P"))))
                .willReturn(List.of(Map.of("OverviewPage", "kyeahoo", "ID", "kyeahoo", "Team", "Karmine Corp")));
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().startsWith("PlayerImages"))))
                .willReturn(List.of(Map.of("Link", "kyeahoo", "FileName", "KC kyeahoo 2026.png", "DateStart", "2026-07-01")));
        given(leaguepediaClient.imageUrls(anyCollection()))
                .willReturn(Map.of("KC kyeahoo 2026.png", "https://static.wikia.nocookie.net/kyeahoo.png"));

        // when
        List<ProPlayerInfo> players = source.players(List.of("Kyeahoo"));

        // then
        assertThat(players).containsExactly(
                new ProPlayerInfo("Kyeahoo", "kyeahoo", "Karmine Corp", "https://static.wikia.nocookie.net/kyeahoo.png"));
    }

    @Test
    @DisplayName("사진은 대회 시작일이 가장 늦은 것으로 고르도록 조인·정렬해 요청한다")
    void players_WhenQueryingImages_JoinTournamentsOrderByLatestStart() {
        // given
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().equals("Players=P"))))
                .willReturn(List.of());
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().startsWith("PlayerImages"))))
                .willReturn(List.of());
        ArgumentCaptor<CargoQuery> query = ArgumentCaptor.forClass(CargoQuery.class);

        // when
        source.players(List.of("Faker"));

        // then
        verify(leaguepediaClient, times(2)).cargoQuery(query.capture());
        CargoQuery images = query.getAllValues().get(1);
        assertThat(images.tables()).isEqualTo("PlayerImages=PI, Tournaments=T");
        assertThat(images.joinOn()).isEqualTo("PI.Tournament=T.OverviewPage");
        assertThat(images.where()).isEqualTo("PI.Link IN ('Faker') AND PI.IsProfileImage = '1'");
        assertThat(images.orderBy()).isEqualTo("T.DateStart DESC, PI.SortDate DESC");
    }

    @Test
    @DisplayName("Players에 없는 선수는 페이지 이름에서 괄호를 뺀 이름을 쓰고, 사진이 없으면 URL은 비어 있다")
    void players_WhenNotFound_FallBackToPageNameWithoutImage() {
        // given
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().equals("Players=P"))))
                .willReturn(List.of());
        given(leaguepediaClient.cargoQuery(argThat(query -> query != null && query.tables().startsWith("PlayerImages"))))
                .willReturn(List.of());
        given(leaguepediaClient.imageUrls(anyCollection())).willReturn(Map.of());

        // when
        List<ProPlayerInfo> players = source.players(List.of("Kratos (Ngô Đức Khánh)"));

        // then
        assertThat(players).containsExactly(new ProPlayerInfo("Kratos (Ngô Đức Khánh)", "Kratos", null, null));
    }
}
