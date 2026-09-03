package dfgg.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.match.RiotMatchSyncService;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private RawMatchRepository rawMatchRepository;

    @Autowired
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private NormalizedMatchParticipantRepository normalizedRepository;

    @Autowired
    private ChampionBuildStatsRepository statsRepository;

    @Autowired
    private CompositionStatsSampleRepository sampleRepository;

    @Autowired
    private StatsAggregationCompletionRepository completionRepository;

    @Autowired
    private RiotMatchSyncService riotMatchSyncService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        completionRepository.deleteAllInBatch();
        sampleRepository.deleteAllInBatch();
        statsRepository.deleteAll();
        normalizedRepository.deleteAllInBatch();
        rawMatchTimelineRepository.deleteAllInBatch();
        rawMatchRepository.deleteAllInBatch();
        playerRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        championRepository.deleteAllInBatch();
    }

    @Test
    void 매치_원본을_중복_호출과_중복_저장_없이_동기화한다() throws Exception {
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

        mockMvc.perform(post("/admin/riot/matches")
                        .param("puuids", "puuid-1"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/admin/riot/matches")
                        .param("puuids", "puuid-1"))
                .andExpect(status().isNoContent());

        RawMatch saved = rawMatchRepository.findById(MATCH_ID).orElseThrow();
        assertThat(saved.getRawData()).isEqualTo(RAW_DATA);
        assertThat(rawMatchRepository.count()).isEqualTo(1);
        RawMatchTimeline savedTimeline = rawMatchTimelineRepository.findById(MATCH_ID).orElseThrow();
        assertThat(savedTimeline.getRawData()).isEqualTo(RAW_TIMELINE);
        assertThat(rawMatchTimelineRepository.count()).isEqualTo(1);
        verify(riotClient, times(2)).getMatchIds("puuid-1", 0, 1);
        verify(riotClient).getRawMatch(MATCH_ID);
        verify(riotClient).getRawMatchTimeline(MATCH_ID);
    }

    @Test
    void 잘못된_조회_범위는_요청을_거부한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches")
                        .param("puuids", "puuid-1")
                        .param("start", "-1")
                        .param("count", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(riotClient);
    }

    @Test
    void 상위_트랜잭션이_있어도_Riot_API_호출_중에는_트랜잭션을_중단한다() {
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

            riotMatchSyncService.syncMatches(List.of("puuid-1"), 0, 20);

            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });

        assertThat(rawMatchRepository.existsById(MATCH_ID)).isTrue();
    }

    @Test
    void 지정한_플레이어만_동기화한다() throws Exception {
        when(riotClient.getMatchIds("puuid-2", 0, 1)).thenReturn(List.of(MATCH_ID));
        when(riotClient.getRawMatch(MATCH_ID)).thenReturn(RAW_DATA);
        when(riotClient.getRawMatchTimeline(MATCH_ID)).thenReturn(RAW_TIMELINE);

        mockMvc.perform(post("/admin/riot/matches")
                        .param("puuids", "puuid-2"))
                .andExpect(status().isNoContent());

        verify(riotClient, never()).getMatchIds("puuid-1", 0, 1);
        verify(riotClient).getMatchIds("puuid-2", 0, 1);
        verify(riotClient, never()).getMatchIds("puuid-3", 0, 1);
        assertThat(rawMatchRepository.existsById(MATCH_ID)).isTrue();
    }

    @Test
    void 저장된_Raw와_Player로_통계를_재집계할_때_Riot_API를_호출하지_않는다() throws Exception {
        championRepository.save(new Champion(
                1L,
                "Aatrox",
                "아트록스",
                List.of(ChampionTag.FIGHTER)
        ));
        itemRepository.save(new Item(3071L, "칠흑의 양날 도끼"));
        playerRepository.save(new Player(
                "puuid-1",
                "KR",
                "PLATINUM",
                "I",
                Instant.parse("2026-08-23T00:00:00Z")
        ));
        rawMatchRepository.save(new RawMatch(MATCH_ID, """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"puuid-1","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","item0":3071,"win":true}
                ]}}
                """));
        rawMatchTimelineRepository.save(new RawMatchTimeline(MATCH_ID, """
                {"metadata":{"participants":["puuid-1"]},"info":{"frames":[
                  {"events":[
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071}
                  ]}
                ]}}
                """));
        assertThat(normalizedRepository.count()).isZero();

        mockMvc.perform(post("/admin/riot/matches/stats")
                        .param("tier", "PLATINUM"))
                .andExpect(status().isNoContent());

        verifyNoInteractions(riotClient);
        assertThat(normalizedRepository.findByMatchId(MATCH_ID))
                .anySatisfy(participant -> {
                    assertThat(participant.getPuuid()).isEqualTo("puuid-1");
                    assertThat(participant.getTier()).isEqualTo("PLATINUM");
                });
        assertThat(sampleRepository.count()).isEqualTo(1);
        assertThat(completionRepository.count()).isEqualTo(1);
        assertThat(statsRepository.findAll()).singleElement().satisfies(stats -> {
            assertThat(stats.getTier()).isEqualTo("PLATINUM");
            assertThat(stats.getGameCount()).isEqualTo(1);
        });
    }
}
