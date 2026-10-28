package dfgg.application.propick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dfgg.domain.propick.ProLeagues;
import dfgg.domain.propick.ProPick;
import dfgg.domain.propick.ProPickItem;
import dfgg.domain.propick.ProPickRepository;
import dfgg.domain.propick.ProPlayer;
import dfgg.infrastructure.config.ProPickProperties;
import dfgg.infrastructure.external.client.LeaguepediaClient;
import dfgg.infrastructure.external.client.LeaguepediaException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProPickCollectionService.class, ProPickWriter.class, ProMetadataService.class, ProPickRowConverter.class,
        ProPickCollectionServiceTest.Settings.class})
@Sql("/sql/pro-pick-collection-test-data.sql")
class ProPickCollectionServiceTest {

    private static final String LCK = "LCK/";
    private static final String LPL = "LPL/";
    private static final String LCK_PLAYOFFS = "LCK/2026 Season/Playoffs";
    private static final String LPL_FINALS = "LPL/2026 Season/Finals";
    private static final LocalDate SINCE = LocalDate.of(2026, 9, 22);

    @MockitoBean
    private LeaguepediaClient leaguepediaClient;

    @MockitoBean
    private ProScoreboardSource scoreboardSource;

    @MockitoBean
    private ProMetadataSource metadataSource;

    @Autowired
    private ProPickCollectionService collectionService;

    @Autowired
    private ProPickRepository pickRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        given(metadataSource.tournaments(anyCollection())).willAnswer(invocation -> {
            Collection<String> pages = invocation.getArgument(0);
            return pages.stream().map(page -> new ProTournamentInfo(page, page, "League", null, null, null)).toList();
        });
        given(metadataSource.players(anyCollection())).willAnswer(invocation -> {
            Collection<String> pages = invocation.getArgument(0);
            return pages.stream()
                    .map(ProPlayer::canonicalPage)
                    .distinct()
                    .map(page -> new ProPlayerInfo(page, page, "Team", null))
                    .toList();
        });
        given(scoreboardSource.fetchLeague(any(), any())).willReturn(List.of());
    }

    @Test
    @DisplayName("설정한 리그마다 최근 30일 행을 받아 픽과 주문·아이템·룬, 대회·선수를 저장하고 결과를 리그별로 남긴다")
    void collect_WhenRowsFetched_SavePicksAndReportPerLeague() {
        // given
        given(scoreboardSource.fetchLeague(LCK, SINCE)).willReturn(List.of(
                row("g1", LCK_PLAYOFFS, "Faker", "1", "Test Champion", "Item 1;Item 2"),
                row("g1", LCK_PLAYOFFS, "Chovy", "2", "Other Champion", "")));

        // when
        ProPickCollectionResult result = collectionService.collect();
        flushAndClear();

        // then
        assertThat(result.leagues())
                .extracting(ProLeagueCollectionResult::league, ProLeagueCollectionResult::fetchedRows,
                        ProLeagueCollectionResult::savedPicks, ProLeagueCollectionResult::failure)
                .containsExactly(tuple(LCK, 2, 2, null), tuple(LPL, 0, 0, null));
        ProPick faker = pickOf("g1", "Faker");
        assertThat(faker.getTournament().getOverviewPage()).isEqualTo(LCK_PLAYOFFS);
        assertThat(faker.getChampionId()).isEqualTo(9001L);
        assertThat(faker.getItems()).extracting(ProPickItem::getSlot, ProPickItem::getItemId)
                .containsExactly(tuple(0, 9001L), tuple(1, 9002L));
        assertThat(faker.getSpells()).hasSize(2);
        assertThat(faker.getRunes()).hasSize(2);
    }

    @Test
    @DisplayName("다시 수집해도 같은 경기·선수의 픽은 하나이고, 바뀐 챔피언과 아이템으로 갱신된다")
    void collect_WhenRunTwice_UpdatePickInPlace() {
        // given
        given(scoreboardSource.fetchLeague(LCK, SINCE)).willReturn(
                List.of(row("g1", LCK_PLAYOFFS, "Faker", "1", "Test Champion", "Item 1;Item 2")),
                List.of(row("g1", LCK_PLAYOFFS, "Faker", "1", "Other Champion", ";Item 1")));
        collectionService.collect();
        flushAndClear();

        // when
        collectionService.collect();
        flushAndClear();

        // then
        assertThat(pickRepository.count()).isEqualTo(1);
        ProPick faker = pickOf("g1", "Faker");
        assertThat(faker.getChampionId()).isEqualTo(9002L);
        assertThat(faker.getItems()).extracting(ProPickItem::getSlot, ProPickItem::getItemId)
                .containsExactly(tuple(1, 9001L));
    }

    @Test
    @DisplayName("다시 수집해 칸이 바뀌면 픽 행 하나만 고친다 — 빈 칸은 배열 안의 NULL이 되고 다른 행은 넣거나 지우지 않는다")
    void collect_WhenSlotsChange_UpdateOnlyPickRow() {
        // given
        given(scoreboardSource.fetchLeague(LCK, SINCE)).willReturn(
                List.of(row("g1", LCK_PLAYOFFS, "Faker", "1", "Test Champion", "Item 1;Item 2")),
                List.of(row("g1", LCK_PLAYOFFS, "Faker", "1", "Test Champion", ";Item 1")));
        collectionService.collect();
        flushAndClear();
        Statistics statistics = statistics();

        // when
        collectionService.collect();
        flushAndClear();

        // then
        assertThat(statistics.getEntityUpdateCount()).isEqualTo(1);
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT item_ids::text FROM pro_picks WHERE source_game_id = 'g1'",
                String.class)).isEqualTo("{NULL,9001,NULL,NULL,NULL,NULL,NULL}");
    }

    @Test
    @DisplayName("같은 내용으로 다시 수집하면 아무것도 쓰지 않는다 — 배열을 새로 만들어도 값이 같으면 바뀐 것으로 보지 않는다")
    void collect_WhenRunTwiceWithSameRows_WriteNothing() {
        // given
        given(scoreboardSource.fetchLeague(LCK, SINCE)).willReturn(
                List.of(row("g1", LCK_PLAYOFFS, "Faker", "1", "Test Champion", "Item 1;Item 2")));
        collectionService.collect();
        flushAndClear();
        Statistics statistics = statistics();

        // when
        collectionService.collect();
        flushAndClear();

        // then
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityInsertCount()).isZero();
    }

    @Test
    @DisplayName("한 리그가 실패해도 나머지는 저장하고, 실패한 리그는 사유를 남기며 그 리그의 기존 픽은 그대로다")
    void collect_WhenOneLeagueFails_KeepOthersAndExistingData() {
        // given
        given(scoreboardSource.fetchLeague(LPL, SINCE)).willReturn(
                List.of(row("lpl-1", LPL_FINALS, "Knight", "1", "Test Champion", "")));
        collectionService.collect();
        flushAndClear();
        given(scoreboardSource.fetchLeague(LCK, SINCE)).willReturn(
                List.of(row("g1", LCK_PLAYOFFS, "Faker", "1", "Test Champion", "")));
        willThrow(new LeaguepediaException("Leaguepedia 재시도 3회를 넘겼습니다: ratelimited"))
                .given(scoreboardSource).fetchLeague(LPL, SINCE);

        // when
        ProPickCollectionResult result = collectionService.collect();
        flushAndClear();

        // then
        assertThat(result.leagues())
                .extracting(ProLeagueCollectionResult::league, ProLeagueCollectionResult::failure)
                .containsExactly(tuple(LCK, null), tuple(LPL, "Leaguepedia 재시도 3회를 넘겼습니다: ratelimited"));
        assertThat(pickRepository.findAll()).extracting(ProPick::getSourceGameId)
                .containsExactlyInAnyOrder("g1", "lpl-1");
    }

    @Test
    @DisplayName("모르는 챔피언 행은 저장하지 않고 사유별 건너뛴 개수를 남긴다")
    void collect_WhenUnknownChampion_SkipAndCount() {
        // given
        given(scoreboardSource.fetchLeague(LCK, SINCE)).willReturn(List.of(
                row("g1", LCK_PLAYOFFS, "Faker", "1", "Test Champion", ""),
                row("g1", LCK_PLAYOFFS, "Zeus", "1", "Zaahen", "")));

        // when
        ProPickCollectionResult result = collectionService.collect();

        // then
        ProLeagueCollectionResult lck = result.leagues().getFirst();
        assertThat(lck.savedPicks()).isEqualTo(1);
        assertThat(lck.skippedRows()).containsExactly(Map.entry(ProPickSkipReason.UNKNOWN_CHAMPION, 1));
        assertThat(pickRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인이 실패하면 아무 리그도 요청하지 않고 모든 리그에 사유를 남긴다")
    void collect_WhenLoginFails_FailAllLeaguesWithoutFetching() {
        // given
        willThrow(new LeaguepediaException("Leaguepedia 로그인 실패: Failed (bad password)"))
                .given(leaguepediaClient).login();

        // when
        ProPickCollectionResult result = collectionService.collect();

        // then
        assertThat(result.leagues())
                .extracting(ProLeagueCollectionResult::league, ProLeagueCollectionResult::failure)
                .containsExactly(tuple(LCK, "Leaguepedia 로그인 실패: Failed (bad password)"),
                        tuple(LPL, "Leaguepedia 로그인 실패: Failed (bad password)"));
        verifyNoInteractions(scoreboardSource);
    }

    @Test
    @DisplayName("설정한 리그 접두사로만, 오늘에서 설정 일수를 뺀 날부터 요청한다")
    void collect_WhenRun_FetchOnlyConfiguredLeaguesSinceLookback() {
        // when
        collectionService.collect();

        // then
        verify(scoreboardSource).fetchLeague(eq(LCK), eq(SINCE));
        verify(scoreboardSource).fetchLeague(eq(LPL), eq(SINCE));
    }

    private ProPick pickOf(String gameId, String playerPage) {
        return pickRepository.findAll().stream()
                .filter(pick -> pick.getSourceGameId().equals(gameId)
                        && pick.getPlayer().getLeaguepediaPage().equals(playerPage))
                .findFirst()
                .orElseThrow();
    }

    private Statistics statistics() {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Map<String, String> row(String gameId, String overviewPage, String link, String side,
                                           String champion, String items) {
        Map<String, String> row = new HashMap<>();
        row.put("GameId", gameId);
        row.put("OverviewPage", overviewPage);
        row.put("DateTime UTC", "2026-10-18 19:48:00");
        row.put("Team", "Team");
        row.put("Side", side);
        row.put("PlayerWin", "Yes");
        row.put("Link", link);
        row.put("Role", "Mid");
        row.put("Champion", champion);
        row.put("SummonerSpells", "Test Flash,Test Ignite");
        row.put("Items", items);
        row.put("Trinket", "");
        row.put("Runes", "Rune 1,Rune 2");
        return row;
    }

    @TestConfiguration
    static class Settings {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ProLeagues proLeagues() {
            return new ProLeagues(List.of(LCK, LPL));
        }

        @Bean
        ProPickProperties proPickProperties() {
            return new ProPickProperties(List.of(LCK, LPL), 30);
        }
    }
}
