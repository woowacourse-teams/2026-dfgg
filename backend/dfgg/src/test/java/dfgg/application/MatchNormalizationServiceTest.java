package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemService;
import dfgg.application.match.CoreItemPurchaseOrderCalculator;
import dfgg.application.match.MatchNormalizationService;
import dfgg.application.player.RiotPlayerSyncService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class MatchNormalizationServiceTest {

    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final NormalizedMatchParticipantRepository participantRepository =
            mock(NormalizedMatchParticipantRepository.class);
    private final RawMatchRepository rawMatchRepository = mock(RawMatchRepository.class);
    private final RawMatchTimelineRepository rawMatchTimelineRepository =
            mock(RawMatchTimelineRepository.class);
    private final ItemService itemService = mock(ItemService.class);
    private final RiotPlayerSyncService riotPlayerSyncService = mock(RiotPlayerSyncService.class);
    private final MatchNormalizationService normalizer = new MatchNormalizationService(
            new ObjectMapper(),
            new CoreItemPurchaseOrderCalculator(),
            playerRepository,
            participantRepository,
            rawMatchRepository,
            rawMatchTimelineRepository,
            itemService,
            riotPlayerSyncService
    );

    @BeforeEach
    void setUp() {
        when(playerRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void 일반_정규화는_Riot_API를_호출하지_않고_저장된_플레이어_티어를_사용한다() {
        String rawMatchData = """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"puuid-1","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","item0":3071,"win":true}
                ]}}
                """;
        String rawTimelineData = """
                {"metadata":{"participants":["puuid-1"]},"info":{"frames":[
                  {"events":[
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071}
                  ]}
                ]}}
                """;
        when(rawMatchRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatch("KR_1", rawMatchData)));
        when(rawMatchTimelineRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatchTimeline("KR_1", rawTimelineData)));
        when(itemService.findCoreItemIds()).thenReturn(Set.of(3071));
        when(playerRepository.findAllById(List.of("puuid-1"))).thenReturn(List.of(
                new Player(
                        "puuid-1",
                        "KR",
                        "PLATINUM",
                        "I",
                        Instant.parse("2026-08-23T00:00:00Z")
                )
        ));

        NormalizedMatch normalized = normalizer.normalize("KR_1");

        InOrder order = inOrder(
                rawMatchRepository,
                rawMatchTimelineRepository,
                itemService,
                playerRepository
        );
        order.verify(rawMatchRepository).findById("KR_1");
        order.verify(rawMatchTimelineRepository).findById("KR_1");
        order.verify(playerRepository).findAllById(List.of("puuid-1"));
        order.verify(itemService).findCoreItemIds();
        order.verify(playerRepository).findAllById(List.of("puuid-1"));
        verifyNoInteractions(riotPlayerSyncService);
        assertThat(normalized.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.puuid()).isEqualTo("puuid-1");
            assertThat(participant.tier()).isEqualTo("PLATINUM");
            assertThat(participant.finalCoreItemIds()).containsExactly(3071);
        });
    }

    @Test
    void 재집계는_Riot_API를_호출하지_않고_저장된_플레이어_티어를_사용한다() {
        String rawMatchData = """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"puuid-1","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","item0":3071,"win":true}
                ]}}
                """;
        String rawTimelineData = """
                {"metadata":{"participants":["puuid-1"]},"info":{"frames":[
                  {"events":[
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071}
                  ]}
                ]}}
                """;
        when(rawMatchRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatch("KR_1", rawMatchData)));
        when(rawMatchTimelineRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatchTimeline("KR_1", rawTimelineData)));
        when(itemService.findCoreItemIds()).thenReturn(Set.of(3071));
        when(playerRepository.findAllById(List.of("puuid-1"))).thenReturn(List.of(
                new Player(
                        "puuid-1",
                        "KR",
                        "PLATINUM",
                        "I",
                        Instant.parse("2026-08-23T00:00:00Z")
                )
        ));

        NormalizedMatch normalized = normalizer.normalizeForRebuild("KR_1");

        verifyNoInteractions(riotPlayerSyncService);
        assertThat(normalized.participants()).singleElement()
                .extracting(NormalizedMatchParticipant::tier)
                .isEqualTo("PLATINUM");
    }

    @Test
    void 일반_정규화는_저장된_티어가_없는_참가자만_동기화한다() {
        String rawMatchData = """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"stored-puuid","participantId":1,"championId":1,"teamId":100,"win":true},
                  {"puuid":"missing-puuid","participantId":2,"championId":2,"teamId":200,"win":false}
                ]}}
                """;
        String rawTimelineData = """
                {"metadata":{"participants":["stored-puuid","missing-puuid"]},"info":{"frames":[]}}
                """;
        Player storedPlayer = new Player(
                "stored-puuid",
                "KR",
                "PLATINUM",
                "I",
                Instant.parse("2026-08-23T00:00:00Z")
        );
        Player syncedPlayer = new Player(
                "missing-puuid",
                "KR",
                "GOLD",
                "I",
                Instant.parse("2026-08-24T00:00:00Z")
        );
        when(rawMatchRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatch("KR_1", rawMatchData)));
        when(rawMatchTimelineRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatchTimeline("KR_1", rawTimelineData)));
        when(itemService.findCoreItemIds()).thenReturn(Set.of());
        when(playerRepository.findAllById(List.of("stored-puuid", "missing-puuid")))
                .thenReturn(List.of(storedPlayer), List.of(storedPlayer, syncedPlayer));

        NormalizedMatch normalized = normalizer.normalize("KR_1");

        verify(riotPlayerSyncService).syncPlayerTiers(List.of("missing-puuid"));
        verifyNoMoreInteractions(riotPlayerSyncService);
        assertThat(normalized.participants())
                .extracting(NormalizedMatchParticipant::tier)
                .containsExactly("PLATINUM", "GOLD");
    }

    @Test
    void 표본_티어_정규화는_참가자별_티어를_조회하지_않고_모두_같은_티어를_사용한다() {
        String rawMatchData = """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"seed-puuid","participantId":1,"championId":1,"teamId":100,"win":true},
                  {"puuid":"other-puuid","participantId":2,"championId":2,"teamId":200,"win":false}
                ]}}
                """;
        String rawTimelineData = """
                {"metadata":{"participants":["seed-puuid","other-puuid"]},"info":{"frames":[]}}
                """;
        when(rawMatchRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatch("KR_1", rawMatchData)));
        when(rawMatchTimelineRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatchTimeline("KR_1", rawTimelineData)));
        when(itemService.findCoreItemIds()).thenReturn(Set.of());

        NormalizedMatch normalized = normalizer.normalizeAsTierSample("KR_1", "PLATINUM");

        assertThat(normalized.participants())
                .extracting(NormalizedMatchParticipant::tier)
                .containsExactly("PLATINUM", "PLATINUM");
        verifyNoInteractions(playerRepository, riotPlayerSyncService);
    }

    @Test
    void 매치_상세와_Timeline을_정규화한다() {
        when(playerRepository.findAllById(List.of("blue-puuid"))).thenReturn(List.of(
                new Player(
                        "blue-puuid",
                        "KR",
                        "PLATINUM",
                        "I",
                        Instant.parse("2026-08-22T00:00:00Z")
                )
        ));

        NormalizedMatch normalized = normalizer.normalize(
                "KR_1234567890",
                """
                        {
                          "info": {
                            "gameVersion": "16.15.1.1",
                            "queueId": 420,
                            "participants": [
                              {
                                "puuid": "blue-puuid",
                                "participantId": 1,
                                "championId": 266,
                                "teamId": 100,
                                "teamPosition": "TOP",
                                "item0": 3071,
                                "item1": 6610,
                                "item2": 0,
                                "win": true
                              }
                            ]
                          }
                        }
                        """,
                """
                        {
                          "metadata": {"participants": ["blue-puuid"]},
                          "info": {
                            "frames": [
                              {"events": [
                                {"timestamp": 100, "type": "ITEM_PURCHASED", "participantId": 1, "itemId": 3071},
                                {"timestamp": 200, "type": "ITEM_PURCHASED", "participantId": 1, "itemId": 6610}
                              ]}
                            ]
                          }
                        }
                        """,
                List.of(3071, 6610)
        );

        assertThat(normalized.matchId()).isEqualTo("KR_1234567890");
        assertThat(normalized.patch()).isEqualTo("16.15");
        assertThat(normalized.queueId()).isEqualTo(420);
        assertThat(normalized.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.puuid()).isEqualTo("blue-puuid");
            assertThat(participant.participantId()).isEqualTo(1);
            assertThat(participant.tier()).isEqualTo("PLATINUM");
            assertThat(participant.finalCoreItemIds()).containsExactly(3071, 6610);
            assertThat(participant.coreItemPurchaseOrder()).containsExactly(3071, 6610);
            assertThat(participant.coreItemPurchaseOrderComplete()).isTrue();
        });
    }

    @Test
    void 매치를_정규화한_뒤_참가자_전체를_한번에_교체한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"puuid-1","participantId":1,"championId":1,"teamId":100,"win":true},
                          {"puuid":"puuid-2","participantId":2,"championId":2,"teamId":200,"win":false}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["puuid-1","puuid-2"]},"info":{"frames":[]}}
                """,
                List.of()
        );
        normalizer.save(normalized);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<NormalizedMatchParticipant>> rows =
                ArgumentCaptor.forClass(Iterable.class);
        InOrder order = inOrder(participantRepository);
        order.verify(participantRepository).deleteByMatchId("KR_1");
        order.verify(participantRepository).flush();
        order.verify(participantRepository).saveAll(rows.capture());
        assertThat(rows.getValue())
                .extracting(NormalizedMatchParticipant::getPuuid)
                .containsExactly("puuid-1", "puuid-2");
    }

    @Test
    void 티어를_찾을_수_없는_참가자는_UNRANKED로_정규화한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"unranked-puuid","participantId":1,"championId":1,"teamId":100,"win":true}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["unranked-puuid"]},"info":{"frames":[]}}
                        """,
                List.of()
        );

        assertThat(normalized.participants()).singleElement()
                .extracting(participant -> participant.tier())
                .isEqualTo("UNRANKED");
    }

    @Test
    void participantId가_없으면_참가자_배열_순서로_보완한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"puuid-1","championId":1,"teamId":100,"win":true},
                          {"puuid":"puuid-2","championId":2,"teamId":200,"win":false}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["puuid-1","puuid-2"]},"info":{"frames":[]}}
                        """,
                List.of()
        );

        assertThat(normalized.participants())
                .extracting(participant -> participant.participantId())
                .containsExactly(1, 2);
    }

    @Test
    void participantId가_없으면_Timeline_metadata의_PUUID_순서를_우선한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"puuid-2","championId":2,"teamId":200,"item0":3071,"win":false},
                          {"puuid":"puuid-1","championId":1,"teamId":100,"item0":3071,"win":true}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["puuid-1","puuid-2"]},"info":{"frames":[
                          {"events":[
                            {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                            {"timestamp":200,"type":"ITEM_PURCHASED","participantId":2,"itemId":3071}
                          ]}
                        ]}}
                        """,
                List.of(3071)
        );

        assertThat(normalized.participants())
                .extracting(participant -> participant.participantId())
                .containsExactly(2, 1);
    }

    @Test
    void BOTTOM_퀘스트_완료로_이동한_신발을_최종_빌드에_포함한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"bottom-puuid","participantId":1,"championId":222,"teamId":100,
                           "teamPosition":"BOTTOM","item0":3071,"item1":6610,"item2":3053,
                           "item3":6333,"item4":6676,"item5":3031,"roleBoundItem":3006,"win":true}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["bottom-puuid"]},"info":{"frames":[
                          {"events":[
                            {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3006},
                            {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                            {"timestamp":300,"type":"ITEM_PURCHASED","participantId":1,"itemId":6610},
                            {"timestamp":400,"type":"ITEM_PURCHASED","participantId":1,"itemId":3053},
                            {"timestamp":500,"type":"ITEM_PURCHASED","participantId":1,"itemId":6333},
                            {"timestamp":600,"type":"ITEM_PURCHASED","participantId":1,"itemId":6676},
                            {"timestamp":700,"type":"ITEM_PURCHASED","participantId":1,"itemId":3031}
                          ]}
                        ]}}
                        """,
                List.of(3071, 6610, 3053, 6333, 6676, 3031, 3006)
        );

        assertThat(normalized.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.finalCoreItemIds())
                    .containsExactly(3071, 6610, 3053, 6333, 6676, 3031, 3006);
            assertThat(participant.coreItemPurchaseOrder())
                    .containsExactly(3006, 3071, 6610, 3053, 6333, 6676, 3031);
            assertThat(participant.coreItemPurchaseOrderComplete()).isTrue();
        });
    }

    @Test
    void BOTTOM이_아닌_포지션의_roleBoundItem은_최종_빌드에서_제외한다() {
        NormalizedMatch normalized = normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                          {"puuid":"top-puuid","participantId":1,"championId":266,"teamId":100,
                           "teamPosition":"TOP","item0":3071,"roleBoundItem":3006,"win":true}
                        ]}}
                        """,
                """
                        {"metadata":{"participants":["top-puuid"]},"info":{"frames":[
                          {"events":[
                            {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                            {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":3006}
                          ]}
                        ]}}
                        """,
                List.of(3071, 3006)
        );

        assertThat(normalized.participants()).singleElement().satisfies(participant ->
                assertThat(participant.finalCoreItemIds()).containsExactly(3071)
        );
    }

    @Test
    void 모든_3티어_신발을_실제로_구매한_신발로_보정한다() {
        Map<Integer, Integer> expectedPurchasedBoots = Map.of(
                3168, 3008,
                3170, 3009,
                3171, 3158,
                3172, 3006,
                3173, 3111,
                3174, 3047,
                3175, 3020,
                3176, 3010
        );

        expectedPurchasedBoots.forEach((tierThreeBoot, purchasedBoot) -> {
            NormalizedMatch normalized = normalizer.normalize(
                    "KR_" + tierThreeBoot,
                    """
                            {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                              {"puuid":"puuid-1","participantId":1,"championId":1,"teamId":100,
                               "teamPosition":"TOP","item0":%d,"win":true}
                            ]}}
                            """.formatted(tierThreeBoot),
                    """
                            {"metadata":{"participants":["puuid-1"]},"info":{"frames":[
                              {"events":[
                                {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":%d}
                              ]}
                            ]}}
                            """.formatted(purchasedBoot),
                    List.of(purchasedBoot)
            );

            assertThat(normalized.participants()).singleElement().satisfies(participant -> {
                assertThat(participant.finalCoreItemIds())
                        .as("tier-three boot %s", tierThreeBoot)
                        .containsExactly(purchasedBoot);
                assertThat(participant.coreItemPurchaseOrder())
                        .as("tier-three boot %s", tierThreeBoot)
                        .containsExactly(purchasedBoot);
                assertThat(participant.coreItemPurchaseOrderComplete()).isTrue();
            });
        });
    }

    @Test
    void 참가자가_없으면_정규화하지_않는다() {
        assertThatThrownBy(() -> normalizer.normalize(
                "KR_1",
                "{\"info\":{\"queueId\":420,\"participants\":[]}}",
                "{\"metadata\":{\"participants\":[]},\"info\":{\"frames\":[]}}",
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("match participants must not be empty");
    }

    @Test
    void 외부_응답의_참가자_목록에_null이_있으면_변환하기_전에_거부한다() {
        assertThatThrownBy(() -> normalizer.normalize(
                "KR_1",
                """
                        {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[null]}}
                        """,
                """
                        {"metadata":{"participants":[]},"info":{"frames":[]}}
                        """,
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("match participant must not be null");
    }
}
