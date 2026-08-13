package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dfgg.infrastructure.external.config.RiotApiProperties;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import dfgg.infrastructure.external.dto.MatchResponse;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class RiotClientTest {

    private static final String PLATFORM_BASE_URL = "https://kr.api.riotgames.com";
    private static final String REGIONAL_BASE_URL = "https://asia.api.riotgames.com";
    private static final String API_KEY = "test-api-key";

    private MockRestServiceServer server;
    private RiotClient client;
    private List<Duration> retryDelays;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        RiotApiProperties properties = new RiotApiProperties(
                API_KEY,
                URI.create(PLATFORM_BASE_URL),
                URI.create(REGIONAL_BASE_URL)
        );
        retryDelays = new ArrayList<>();
        RiotRateLimitExecutor rateLimitExecutor = new RiotRateLimitExecutor(
                Clock.fixed(Instant.parse("2026-08-06T08:00:00Z"), ZoneOffset.UTC),
                retryDelays::add
        );
        client = new RiotClient(builder, properties, rateLimitExecutor);
    }

    @Test
    void PUUID로_솔로_랭크_매치_ID를_조회한다() {
        server.expect(requestTo(REGIONAL_BASE_URL
                        + "/lol/match/v5/matches/by-puuid/encrypted-puuid/ids?queue=420&start=5&count=2"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        [
                          "KR_1234567890",
                          "KR_0987654321"
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<String> matchIds = client.getMatchIds("encrypted-puuid", 5, 2);

        assertThat(matchIds).containsExactly("KR_1234567890", "KR_0987654321");
        server.verify();
    }

    @Test
    void 매치_ID_호출이_제한되면_Retry_After_이후에_재시도한다() {
        String requestUrl = REGIONAL_BASE_URL
                + "/lol/match/v5/matches/by-puuid/encrypted-puuid/ids?queue=420&start=0&count=20";
        server.expect(requestTo(requestUrl))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "2"));
        server.expect(requestTo(requestUrl))
                .andRespond(withSuccess("[\"KR_1234567890\"]", MediaType.APPLICATION_JSON));

        List<String> matchIds = client.getMatchIds("encrypted-puuid");

        assertThat(matchIds).containsExactly("KR_1234567890");
        assertThat(retryDelays).containsExactly(Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void 매치_ID는_Riot_기본_페이지로_조회할_수_있다() {
        server.expect(requestTo(REGIONAL_BASE_URL
                        + "/lol/match/v5/matches/by-puuid/encrypted-puuid/ids?queue=420&start=0&count=20"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<String> matchIds = client.getMatchIds("encrypted-puuid");

        assertThat(matchIds).isEmpty();
        server.verify();
    }

    @Test
    void 매치_ID_응답_본문이_없으면_예외가_발생한다() {
        server.expect(requestTo(REGIONAL_BASE_URL
                        + "/lol/match/v5/matches/by-puuid/encrypted-puuid/ids?queue=420&start=0&count=20"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.getMatchIds("encrypted-puuid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[Error] Riot Match IDs response is empty");

        server.verify();
    }

    @Test
    void 매치_ID_조회_범위를_검증한다() {
        assertThatThrownBy(() -> client.getMatchIds("encrypted-puuid", -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("start must not be negative");

        assertThatThrownBy(() -> client.getMatchIds("encrypted-puuid", 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count must be between 0 and 100");
    }

    @Test
    void 매치_ID로_매치_상세를_조회한다() {
        server.expect(requestTo(REGIONAL_BASE_URL + "/lol/match/v5/matches/KR_1234567890"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {
                          "metadata": {
                            "dataVersion": "2",
                            "matchId": "KR_1234567890",
                            "participants": ["blue-puuid"]
                          },
                          "info": {
                            "gameDuration": 1832,
                            "participants": [
                              {
                                "puuid": "blue-puuid",
                                "participantId": 1,
                                "championId": 266,
                                "championName": "Aatrox",
                                "teamId": 100,
                                "teamPosition": "TOP",
                                "kills": 8,
                                "item0": 3071,
                                "item1": 6610,
                                "item2": 3053,
                                "item3": 3111,
                                "item4": 6333,
                                "item5": 0,
                                "item6": 3364,
                                "roleBoundItem": 3006,
                                "win": true
                              }
                            ],
                            "queueId": 420
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        MatchResponse match = client.getMatch("KR_1234567890");

        assertThat(match.info().participants()).singleElement().satisfies(participant -> {
            assertThat(participant.puuid()).isEqualTo("blue-puuid");
            assertThat(participant.participantId()).isEqualTo(1);
            assertThat(participant.championId()).isEqualTo(266);
            assertThat(participant.teamId()).isEqualTo(100);
            assertThat(participant.teamPosition()).isEqualTo("TOP");
            assertThat(participant.item0()).isEqualTo(3071);
            assertThat(participant.roleBoundItem()).isEqualTo(3006);
            assertThat(participant.win()).isTrue();
        });
        assertThat(match.info().gameVersion()).isNull();
        assertThat(match.info().queueId()).isEqualTo(420);
        server.verify();
    }

    @Test
    void 매치_Timeline을_파싱한다() {
        server.expect(requestTo(
                        REGIONAL_BASE_URL + "/lol/match/v5/matches/KR_1234567890/timeline"))
                .andRespond(withSuccess("""
                        {
                          "metadata": {
                            "dataVersion": "2",
                            "matchId": "KR_1234567890",
                            "participants": ["blue-puuid", "red-puuid"]
                          },
                          "info": {
                            "frameInterval": 60000,
                            "frames": [
                              {
                                "events": [
                                  {
                                    "timestamp": 1234,
                                    "type": "ITEM_PURCHASED",
                                    "participantId": 1,
                                    "itemId": 3071
                                  },
                                  {
                                    "timestamp": 2345,
                                    "type": "ITEM_SOLD",
                                    "participantId": 1,
                                    "itemId": 1036
                                  },
                                  {
                                    "timestamp": 3456,
                                    "type": "ITEM_UNDO",
                                    "participantId": 1,
                                    "beforeId": 3071,
                                    "afterId": 0
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        MatchTimelineResponse timeline = client.getMatchTimeline("KR_1234567890");

        assertThat(timeline.metadata().participants())
                .containsExactly("blue-puuid", "red-puuid");
        assertThat(timeline.puuidForParticipantId(1)).contains("blue-puuid");
        assertThat(timeline.puuidForParticipantId(3)).isEmpty();
        assertThat(timeline.info().frameInterval()).isEqualTo(60000L);
        List<MatchTimelineResponse.Event> parsedEvents = timeline.info().frames().get(0).events();
        assertThat(parsedEvents).hasSize(3);
        assertThat(parsedEvents.get(0).itemId()).isEqualTo(3071);
        assertThat(parsedEvents.get(2).beforeId()).isEqualTo(3071);
        assertThat(parsedEvents.get(2).afterId()).isEqualTo(0);
        server.verify();
    }

    @Test
    void 매치_상세_응답_본문이_없으면_예외가_발생한다() {
        server.expect(requestTo(REGIONAL_BASE_URL + "/lol/match/v5/matches/KR_1234567890"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.getMatch("KR_1234567890"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[Error] Riot Match response is empty");

        server.verify();
    }

    @Test
    void 매치_ID는_비어_있을_수_없다() {
        assertThatThrownBy(() -> client.getMatch(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("matchId must not be blank");
    }

    @Test
    void 매치_원본_응답을_문자열로_조회한다() {
        String rawData = """
                {"info":{"participants":[]}}
                """;
        server.expect(requestTo(REGIONAL_BASE_URL + "/lol/match/v5/matches/KR_1234567890"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(rawData, MediaType.APPLICATION_JSON));

        String response = client.getRawMatch("KR_1234567890");

        assertThat(response).isEqualTo(rawData);
        server.verify();
    }

    @Test
    void 매치_원본_응답_본문이_없으면_예외가_발생한다() {
        server.expect(requestTo(REGIONAL_BASE_URL + "/lol/match/v5/matches/KR_1234567890"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.getRawMatch("KR_1234567890"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[Error] Riot raw Match response is empty");

        server.verify();
    }

    @Test
    void 매치_Timeline_원본_응답을_문자열로_조회한다() {
        String rawData = """
                {"info":{"frames":[]}}
                """;
        server.expect(requestTo(
                        REGIONAL_BASE_URL + "/lol/match/v5/matches/KR_1234567890/timeline"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(rawData, MediaType.APPLICATION_JSON));

        String response = client.getRawMatchTimeline("KR_1234567890");

        assertThat(response).isEqualTo(rawData);
        server.verify();
    }

    @Test
    void 매치_Timeline_원본_응답_본문이_없으면_예외가_발생한다() {
        server.expect(requestTo(
                        REGIONAL_BASE_URL + "/lol/match/v5/matches/KR_1234567890/timeline"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.getRawMatchTimeline("KR_1234567890"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[Error] Riot raw Match timeline response is empty");

        server.verify();
    }

    @Test
    void 티어와_디비전의_리그_엔트리를_페이지로_조회한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/I?page=2"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        [
                          {
                            "puuid": "encrypted-puuid",
                            "queueType": "RANKED_SOLO_5x5",
                            "tier": "EMERALD",
                            "rank": "I",
                            "leaguePoints": 73,
                            "wins": 120,
                            "losses": 100,
                            "hotStreak": true
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<LeagueEntryResponse> entries = client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "I",
                2
        );

        assertThat(entries).containsExactly(new LeagueEntryResponse(
                "encrypted-puuid",
                "RANKED_SOLO_5x5",
                "EMERALD",
                "I",
                73,
                120,
                100
        ));
        server.verify();
    }

    @Test
    void 조회_결과가_빈_배열이면_그대로_반환한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/IV?page=3"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<LeagueEntryResponse> entries = client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "IV",
                3
        );

        assertThat(entries).isEmpty();
        server.verify();
    }

    @Test
    void 응답_본문이_없으면_예외가_발생한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/II?page=1"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "II",
                1
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[Error] Riot League entries response is empty");

        server.verify();
    }

    @Test
    void 호출이_제한되면_Retry_After_이후에_재시도한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/III?page=1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "7"));
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/III?page=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<LeagueEntryResponse> entries = client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "III",
                1
        );

        assertThat(entries).isEmpty();
        assertThat(retryDelays).containsExactly(Duration.ofSeconds(7));

        server.verify();
    }

    @Test
    void Retry_After가_없으면_429_예외를_즉시_전파한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/III?page=1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "III",
                1
        )).isInstanceOfSatisfying(HttpClientErrorException.TooManyRequests.class, exception ->
                assertThat(exception.getMessage()).doesNotContain(API_KEY));

        assertThat(retryDelays).isEmpty();

        server.verify();
    }

    @Test
    void 호출_제한_재시도_횟수를_초과하면_429_예외를_전파한다() {
        server.expect(
                        ExpectedCount.times(3),
                        requestTo(PLATFORM_BASE_URL
                                + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/III?page=1")
                )
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "1"));

        assertThatThrownBy(() -> client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "III",
                1
        )).isInstanceOf(HttpClientErrorException.TooManyRequests.class);

        assertThat(retryDelays).containsExactly(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );

        server.verify();
    }

    @Test
    void 페이지는_1_이상이어야_한다() {
        assertThatThrownBy(() -> client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "I",
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be greater than zero");
    }
}
