package dfgg.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.RiotMatchSyncService;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class RiotMatchControllerIntegrationTest {

    private static final String MATCH_ID = "KR_1234567890";
    private static final String RAW_DATA = "{\"info\":{\"participants\":[]}}";
    private static final String RAW_TIMELINE = "{\"info\":{\"frames\":[]}}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiotClient riotClient;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private RawMatchRepository rawMatchRepository;

    @Autowired
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @Autowired
    private RiotMatchSyncService riotMatchSyncService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        rawMatchTimelineRepository.deleteAll();
        rawMatchRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void 매치_원본을_중복_호출과_중복_저장_없이_동기화한다() throws Exception {
        playerRepository.saveAndFlush(new Player(
                "puuid-1",
                "KR",
                Instant.parse("2026-08-06T08:00:00Z")
        ));
        playerRepository.saveAndFlush(new Player(
                "puuid-na",
                "NA",
                Instant.parse("2026-08-06T08:00:00Z")
        ));
        when(riotClient.getMatchIds("puuid-1", 0, 1)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(MATCH_ID);
        });
        when(riotClient.getRawMatch(MATCH_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return RAW_DATA;
        });
        when(riotClient.getRawMatchTimeline(MATCH_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return RAW_TIMELINE;
        });

        mockMvc.perform(post("/admin/riot/matches"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/admin/riot/matches"))
                .andExpect(status().isNoContent());

        RawMatch saved = rawMatchRepository.findById(MATCH_ID).orElseThrow();
        assertThat(saved.getRawData()).isEqualTo(RAW_DATA);
        assertThat(rawMatchRepository.count()).isEqualTo(1);
        RawMatchTimeline savedTimeline = rawMatchTimelineRepository.findById(MATCH_ID).orElseThrow();
        assertThat(savedTimeline.getRawData()).isEqualTo(RAW_TIMELINE);
        assertThat(rawMatchTimelineRepository.count()).isEqualTo(1);
        verify(riotClient, times(2)).getMatchIds("puuid-1", 0, 1);
        verify(riotClient, never()).getMatchIds("puuid-na", 0, 1);
        verify(riotClient).getRawMatch(MATCH_ID);
        verify(riotClient).getRawMatchTimeline(MATCH_ID);
    }

    @Test
    void 잘못된_조회_범위는_요청을_거부한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches")
                        .param("playerPage", "-1")
                        .param("playerCount", "0")
                        .param("start", "-1")
                        .param("count", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(riotClient);
    }

    @Test
    void 상위_트랜잭션이_있어도_Riot_API_호출_중에는_트랜잭션을_중단한다() {
        playerRepository.saveAndFlush(new Player(
                "puuid-1",
                "KR",
                Instant.parse("2026-08-06T08:00:00Z")
        ));
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(MATCH_ID);
        });
        when(riotClient.getRawMatch(MATCH_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return RAW_DATA;
        });
        when(riotClient.getRawMatchTimeline(MATCH_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return RAW_TIMELINE;
        });

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

            riotMatchSyncService.syncMatches(0, 20, 0, 20);

            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });

        assertThat(rawMatchRepository.existsById(MATCH_ID)).isTrue();
    }

    @Test
    void 지정한_플레이어_배치만_동기화한다() throws Exception {
        playerRepository.saveAllAndFlush(List.of(
                new Player("puuid-1", "KR", Instant.parse("2026-08-06T08:00:00Z")),
                new Player("puuid-2", "KR", Instant.parse("2026-08-06T08:00:00Z")),
                new Player("puuid-3", "KR", Instant.parse("2026-08-06T08:00:00Z"))
        ));
        when(riotClient.getMatchIds("puuid-2", 0, 1)).thenReturn(List.of(MATCH_ID));
        when(riotClient.getRawMatch(MATCH_ID)).thenReturn(RAW_DATA);
        when(riotClient.getRawMatchTimeline(MATCH_ID)).thenReturn(RAW_TIMELINE);

        mockMvc.perform(post("/admin/riot/matches")
                        .param("playerPage", "1")
                        .param("playerCount", "1"))
                .andExpect(status().isNoContent());

        verify(riotClient, never()).getMatchIds("puuid-1", 0, 1);
        verify(riotClient).getMatchIds("puuid-2", 0, 1);
        verify(riotClient, never()).getMatchIds("puuid-3", 0, 1);
        assertThat(rawMatchRepository.existsById(MATCH_ID)).isTrue();
    }
}
