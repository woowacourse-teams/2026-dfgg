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
import dfgg.infrastructure.external.dto.LeagueListResponse;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        RiotApiProperties properties = new RiotApiProperties(
                API_KEY,
                URI.create(PLATFORM_BASE_URL),
                URI.create(REGIONAL_BASE_URL),
                18,
                95
        );
        clock = new MutableClock(Instant.parse("2026-08-06T08:00:00Z"), ZoneOffset.UTC);
        retryDelays = new ArrayList<>();
        RiotRateLimitExecutor rateLimitExecutor = new RiotRateLimitExecutor(
                clock,
                duration -> {
                    retryDelays.add(duration);
                    clock.advance(duration);
                }
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
    void PUUID로_리그_엔트리를_조회한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/by-puuid/encrypted-puuid"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andRespond(withSuccess("""
                        [{
                          "puuid": "encrypted-puuid",
                          "queueType": "RANKED_SOLO_5x5",
                          "tier": "PLATINUM",
                          "rank": "I",
                          "leaguePoints": 50,
                          "wins": 20,
                          "losses": 10
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<LeagueEntryResponse> entries = client.getLeagueEntriesByPuuid("encrypted-puuid");

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.queueType()).isEqualTo("RANKED_SOLO_5x5");
            assertThat(entry.tier()).isEqualTo("PLATINUM");
            assertThat(entry.rank()).isEqualTo("I");
        });
        server.verify();
    }

    @Test
    void Master_리그를_조회한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/masterleagues/by-queue/RANKED_SOLO_5x5"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {
                          "tier": "MASTER",
                          "queue": "RANKED_SOLO_5x5",
                          "entries": [
                            {
                              "puuid": "master-puuid",
                              "leaguePoints": 1399,
                              "rank": "I",
                              "wins": 242,
                              "losses": 183,
                              "veteran": false,
                              "inactive": false,
                              "freshBlood": false,
                              "hotStreak": false
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        LeagueListResponse league = client.getMasterLeague("RANKED_SOLO_5x5");

        assertThat(league.tier()).isEqualTo("MASTER");
        assertThat(league.queue()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(league.entries()).containsExactly(new LeagueEntryResponse(
                "master-puuid",
                null,
                null,
                "I",
                1399,
                242,
                183
        ));
        server.verify();
    }

    @Test
    void Master_리그_응답_본문이_없으면_예외가_발생한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/masterleagues/by-queue/RANKED_SOLO_5x5"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.getMasterLeague("RANKED_SOLO_5x5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[Error] Riot Master league response is empty");

        server.verify();
    }

    @Test
    void Grandmaster_리그를_조회한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/grandmasterleagues/by-queue/RANKED_SOLO_5x5"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {
                          "tier": "GRANDMASTER",
                          "queue": "RANKED_SOLO_5x5",
                          "entries": [
                            {
                              "puuid": "grandmaster-puuid",
                              "leaguePoints": 821,
                              "rank": "I",
                              "wins": 190,
                              "losses": 151,
                              "veteran": true,
                              "inactive": false,
                              "freshBlood": false,
                              "hotStreak": true
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        LeagueListResponse league = client.getGrandmasterLeague("RANKED_SOLO_5x5");

        assertThat(league.tier()).isEqualTo("GRANDMASTER");
        assertThat(league.queue()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(league.entries()).containsExactly(new LeagueEntryResponse(
                "grandmaster-puuid",
                null,
                null,
                "I",
                821,
                190,
                151
        ));
        server.verify();
    }

    @Test
    void Grandmaster_리그_응답_본문이_없으면_예외가_발생한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/grandmasterleagues/by-queue/RANKED_SOLO_5x5"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.getGrandmasterLeague("RANKED_SOLO_5x5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[Error] Riot Grandmaster league response is empty");

        server.verify();
    }

    @Test
    void Challenger_리그를_조회한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5"))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {
                          "tier": "CHALLENGER",
                          "queue": "RANKED_SOLO_5x5",
                          "entries": [
                            {
                              "puuid": "challenger-puuid",
                              "leaguePoints": 1604,
                              "rank": "I",
                              "wins": 271,
                              "losses": 211,
                              "veteran": true,
                              "inactive": false,
                              "freshBlood": false,
                              "hotStreak": true
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        LeagueListResponse league = client.getChallengerLeague("RANKED_SOLO_5x5");

        assertThat(league.tier()).isEqualTo("CHALLENGER");
        assertThat(league.queue()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(league.entries()).containsExactly(new LeagueEntryResponse(
                "challenger-puuid",
                null,
                null,
                "I",
                1604,
                271,
                211
        ));
        server.verify();
    }

    @Test
    void Challenger_리그_응답_본문이_없으면_예외가_발생한다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.getChallengerLeague("RANKED_SOLO_5x5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[Error] Riot Challenger league response is empty");

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
    void 호출_제한이_반복되어도_Retry_After_이후에_성공할_때까지_재시도한다() {
        server.expect(
                        ExpectedCount.times(4),
                        requestTo(PLATFORM_BASE_URL
                                + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/III?page=1")
                )
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "1"));
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
        assertThat(retryDelays).containsExactly(
                Duration.ofMillis(1_264),
                Duration.ofMillis(1_264),
                Duration.ofMillis(1_264),
                Duration.ofMillis(1_264)
        );

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "-1", "0", "9223372036854775807"})
    void Retry_After가_올바르지_않으면_429_예외를_즉시_전파한다(String retryAfter) {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/III?page=1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, retryAfter));

        assertThatThrownBy(() -> client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "III",
                1
        )).isInstanceOf(HttpClientErrorException.TooManyRequests.class);

        assertThat(retryDelays).isEmpty();

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

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
