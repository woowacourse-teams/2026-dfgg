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
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class RiotClientTest {

    private static final String PLATFORM_BASE_URL = "https://kr.api.riotgames.com";
    private static final String REGIONAL_BASE_URL = "https://asia.api.riotgames.com";
    private static final String API_KEY = "test-api-key";

    private MockRestServiceServer server;
    private RiotClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        RiotApiProperties properties = new RiotApiProperties(
                API_KEY,
                URI.create(PLATFORM_BASE_URL),
                URI.create(REGIONAL_BASE_URL)
        );
        client = new RiotClient(builder, properties);
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
    void 호출이_제한되면_Retry_After를_포함한_예외가_전파된다() {
        server.expect(requestTo(PLATFORM_BASE_URL
                        + "/lol/league/v4/entries/RANKED_SOLO_5x5/EMERALD/III?page=1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "7"));

        assertThatThrownBy(() -> client.getLeagueEntries(
                "RANKED_SOLO_5x5",
                "EMERALD",
                "III",
                1
        ))
                .isInstanceOfSatisfying(HttpClientErrorException.TooManyRequests.class, exception ->
                        assertThat(exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                                .isEqualTo("7"));

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
