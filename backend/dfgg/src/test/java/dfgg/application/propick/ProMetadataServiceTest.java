package dfgg.application.propick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

import dfgg.domain.propick.ProPlayer;
import dfgg.domain.propick.ProPlayerRepository;
import dfgg.domain.propick.ProTournament;
import dfgg.domain.propick.ProTournamentRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProMetadataService.class, ProMetadataServiceTest.FixedClock.class})
@Sql(statements = {"DELETE FROM pro_picks", "DELETE FROM pro_players", "DELETE FROM pro_tournaments"})
class ProMetadataServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");
    private static final String PLAYOFFS = "LCK/2026 Season/Playoffs";

    @MockitoBean
    private ProMetadataSource metadataSource;

    @Autowired
    private ProMetadataService metadataService;

    @Autowired
    private ProTournamentRepository tournamentRepository;

    @Autowired
    private ProPlayerRepository playerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("처음 보는 대회는 저장하고 페이지 이름으로 찾을 수 있게 돌려준다")
    void upsertTournaments_WhenNew_SaveAndReturnByPage() {
        // given
        given(metadataSource.tournaments(anyCollection())).willReturn(List.of(new ProTournamentInfo(
                PLAYOFFS, "LCK 2026 Playoffs", "LoL Champions Korea", "Korea", LocalDate.of(2026, 9, 1), null)));

        // when
        Map<String, ProTournament> tournaments = metadataService.upsertTournaments(List.of(PLAYOFFS));

        // then
        assertThat(tournaments.get(PLAYOFFS).getId()).isNotNull();
        assertThat(tournamentRepository.findAll()).singleElement()
                .satisfies(tournament -> {
                    assertThat(tournament.getName()).isEqualTo("LCK 2026 Playoffs");
                    assertThat(tournament.getLeague()).isEqualTo("LoL Champions Korea");
                    assertThat(tournament.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
                });
    }

    @Test
    @DisplayName("이미 있는 대회는 새 행을 만들지 않고 정보를 갱신한다")
    void upsertTournaments_WhenExists_UpdateInPlace() {
        // given
        given(metadataSource.tournaments(anyCollection())).willReturn(
                List.of(new ProTournamentInfo(PLAYOFFS, "LCK Playoffs", "LCK", null, null, null)),
                List.of(new ProTournamentInfo(PLAYOFFS, "LCK 2026 Playoffs", "LCK", "Korea",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28))));
        metadataService.upsertTournaments(List.of(PLAYOFFS));
        entityManager.flush();
        entityManager.clear();

        // when
        metadataService.upsertTournaments(List.of(PLAYOFFS));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(tournamentRepository.findAll()).singleElement()
                .satisfies(tournament -> {
                    assertThat(tournament.getName()).isEqualTo("LCK 2026 Playoffs");
                    assertThat(tournament.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 28));
                });
    }

    @Test
    @DisplayName("처음 보는 선수는 이름·팀·사진 URL과 갱신 시각을 저장한다")
    void upsertPlayers_WhenNew_SaveProfileWithUpdatedAt() {
        // given
        given(metadataSource.players(anyCollection())).willReturn(List.of(
                new ProPlayerInfo("Faker", "Faker", "T1", "https://static.wikia.nocookie.net/faker.png")));

        // when
        Map<String, ProPlayer> players = metadataService.upsertPlayers(List.of("Faker"));

        // then
        assertThat(players.get("Faker").getId()).isNotNull();
        assertThat(playerRepository.findAll()).singleElement()
                .satisfies(player -> {
                    assertThat(player.getTeam()).isEqualTo("T1");
                    assertThat(player.getImageUrl()).isEqualTo("https://static.wikia.nocookie.net/faker.png");
                    assertThat(player.getUpdatedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
                });
    }

    @Test
    @DisplayName("경기 기록의 링크가 소문자로 시작해도 같은 선수로 찾는다")
    void upsertPlayers_WhenLinkLowercase_ReturnByCanonicalPage() {
        // given
        given(metadataSource.players(anyCollection())).willReturn(List.of(
                new ProPlayerInfo("Kyeahoo", "Kyeahoo", "DRX", null)));

        // when
        Map<String, ProPlayer> players = metadataService.upsertPlayers(List.of("kyeahoo"));

        // then
        assertThat(players).containsOnlyKeys("Kyeahoo");
        assertThat(playerRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 있는 선수는 팀을 갱신하고, 새 사진을 못 찾았으면 기존 사진을 남긴다")
    void upsertPlayers_WhenExistsWithoutNewImage_UpdateTeamKeepImage() {
        // given
        given(metadataSource.players(anyCollection())).willReturn(
                List.of(new ProPlayerInfo("Faker", "Faker", "T1", "https://static.wikia.nocookie.net/faker.png")),
                List.of(new ProPlayerInfo("Faker", "Faker", "T1 Academy", null)));
        metadataService.upsertPlayers(List.of("Faker"));
        entityManager.flush();
        entityManager.clear();

        // when
        metadataService.upsertPlayers(List.of("Faker"));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(playerRepository.findAll()).singleElement()
                .satisfies(player -> {
                    assertThat(player.getTeam()).isEqualTo("T1 Academy");
                    assertThat(player.getImageUrl()).isEqualTo("https://static.wikia.nocookie.net/faker.png");
                });
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
