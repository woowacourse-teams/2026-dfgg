package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dfgg.infrastructure.external.config.LeaguepediaProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class LeaguepediaClientTest {

    private static final String BASE_URL = "https://lol.fandom.com";
    private static final String API = BASE_URL + "/api.php";
    private static final String USERNAME = "Account@dfgg";
    private static final String PASSWORD = "test-bot-password";
    private static final String USER_AGENT = "dfgg-test/1.0";
    private static final Duration INTERVAL = Duration.ofSeconds(12);
    private static final Duration BACKOFF = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 3;

    private static final CargoQuery SCOREBOARD = new CargoQuery(
            "ScoreboardPlayers=SP",
            "SP.GameId, SP.Link, SP.Champion",
            "SP.OverviewPage LIKE 'LCK/%' AND SP.DateTime_UTC >= '2026-09-22'",
            "SP.DateTime_UTC DESC");

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private FakeTime time;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        time = new FakeTime();
    }

    @Test
    @DisplayName("로그인 토큰을 받은 응답의 쿠키를 실어 봇 이름·비밀번호·토큰으로 로그인한다")
    void login_WhenTokenIssued_PostCredentialsWithSessionCookie() {
        // given
        expectLoginToken();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("action", "login");
        form.add("lgname", USERNAME);
        form.add("lgpassword", PASSWORD);
        form.add("lgtoken", "token+\\");
        form.add("format", "json");
        server.expect(requestTo(API))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "lolwiki_session=abc"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("""
                        {"login":{"result":"Success","lgusername":"Account@dfgg"}}
                        """, MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        client.login();

        // then
        server.verify();
    }

    @Test
    @DisplayName("로그인이 실패하면 사유를 담은 예외를 던지고, 비밀번호는 메시지에 넣지 않는다")
    void login_WhenResultFailed_ThrowWithReasonWithoutPassword() {
        // given
        expectLoginToken();
        server.expect(requestTo(API))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"login":{"result":"Failed","reason":"Incorrect username or password entered."}}
                        """, MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when & then
        assertThatThrownBy(client::login)
                .isInstanceOf(LeaguepediaException.class)
                .hasMessageContaining("Failed")
                .hasMessageContaining("Incorrect username or password entered.")
                .message().doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("로그인 토큰을 받지 못하면 로그인을 보내지 않고 예외를 던진다")
    void login_WhenNoToken_ThrowWithoutPostingCredentials() {
        // given
        server.expect(ExpectedCount.once(), requestTo(Matchers.startsWith(API)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"error":{"code":"readapidenied","info":"You need read permission"}}
                        """, MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when & then
        assertThatThrownBy(client::login)
                .isInstanceOf(LeaguepediaException.class)
                .hasMessageContaining("readapidenied");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource(value = {"'', test-bot-password", "Account@dfgg, ''", "'  ', '  '"})
    @DisplayName("봇 이름이나 비밀번호가 비어 있으면 아무 요청도 보내지 않고 예외를 던진다")
    void login_WhenCredentialsBlank_ThrowWithoutRequest(String username, String password) {
        // given
        LeaguepediaClient client = client(username, password);

        // when & then
        assertThatThrownBy(client::login)
                .isInstanceOf(LeaguepediaException.class)
                .hasMessageContaining("LEAGUEPEDIA_BOT_USERNAME");
        server.verify();
    }

    @Test
    @DisplayName("설정 문자열에는 비밀번호가 드러나지 않는다")
    void toString_WhenPasswordSet_HidePassword() {
        // given
        LeaguepediaProperties properties = properties(USERNAME, PASSWORD);

        // when & then
        assertThat(properties.toString()).doesNotContain(PASSWORD).contains(USERNAME);
    }

    @Test
    @DisplayName("cargoquery에 테이블·필드·조건·정렬·500행·offset·maxlag를 보내고, 로그인 쿠키를 싣는다")
    void cargoQuery_WhenLoggedIn_SendQueryParamsWithLoginCookies() {
        // given
        expectLoginToken();
        server.expect(requestTo(API))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"login":{"result":"Success"}}
                        """, MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.SET_COOKIE, "lolwikiUserID=42; path=/"));
        server.expect(requestTo(Matchers.startsWith(API)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("action", "cargoquery"))
                .andExpect(queryParam("tables", "ScoreboardPlayers%3DSP"))
                .andExpect(queryParam("fields", "SP.GameId,%20SP.Link,%20SP.Champion"))
                .andExpect(queryParam("where",
                        "SP.OverviewPage%20LIKE%20'LCK/%25'%20AND%20SP.DateTime_UTC%20%3E%3D%20'2026-09-22'"))
                .andExpect(queryParam("order_by", "SP.DateTime_UTC%20DESC"))
                .andExpect(queryParam("limit", "500"))
                .andExpect(queryParam("offset", "0"))
                .andExpect(queryParam("maxlag", "5"))
                .andExpect(queryParam("format", "json"))
                .andExpect(header(HttpHeaders.COOKIE, Matchers.allOf(
                        Matchers.containsString("lolwiki_session=abc"),
                        Matchers.containsString("lolwikiUserID=42"))))
                .andRespond(withSuccess(cargoPage(0, 2), MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);
        client.login();

        // when
        List<Map<String, String>> rows = client.cargoQuery(SCOREBOARD);

        // then
        assertThat(rows).extracting(row -> row.get("GameId")).containsExactly("game-0", "game-1");
        assertThat(rows.getFirst()).containsEntry("DateTime UTC", "2026-10-18 19:48:00");
        server.verify();
    }

    @Test
    @DisplayName("500행이 오면 offset을 500 늘려 다음 페이지를 이어 받는다")
    void cargoQuery_WhenFullPage_FetchNextPage() {
        // given
        expectCargoPage(0, cargoPage(0, 500));
        expectCargoPage(500, cargoPage(500, 3));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        List<Map<String, String>> rows = client.cargoQuery(SCOREBOARD);

        // then
        assertThat(rows).hasSize(503);
        assertThat(rows.getLast()).containsEntry("GameId", "game-502");
        server.verify();
    }

    @Test
    @DisplayName("마지막 페이지가 딱 500행이면 빈 페이지를 받고 멈춘다")
    void cargoQuery_WhenLastPageExactlyFull_StopAtEmptyPage() {
        // given
        expectCargoPage(0, cargoPage(0, 500));
        expectCargoPage(500, cargoPage(500, 0));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        List<Map<String, String>> rows = client.cargoQuery(SCOREBOARD);

        // then
        assertThat(rows).hasSize(500);
        server.verify();
    }

    @Test
    @DisplayName("cargoquery가 오류를 돌려주면 오류 코드를 담은 예외를 던진다")
    void cargoQuery_WhenErrorResponse_ThrowWithCode() {
        // given
        expectCargoPage(0, """
                {"error":{"code":"internal_api_error_MWException","info":"Field does not exist"}}
                """);
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when & then
        assertThatThrownBy(() -> client.cargoQuery(SCOREBOARD))
                .isInstanceOf(LeaguepediaException.class)
                .hasMessageContaining("internal_api_error_MWException");
    }

    @Test
    @DisplayName("첫 요청은 바로 보내고, 다음 요청은 직전 요청에서 12초가 지날 때까지 기다린다")
    void cargoQuery_WhenSeveralPages_WaitIntervalBetweenRequests() {
        // given
        expectCargoPage(0, cargoPage(0, 500));
        expectCargoPage(500, cargoPage(500, 500));
        expectCargoPage(1000, cargoPage(1000, 1));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        client.cargoQuery(SCOREBOARD);

        // then
        assertThat(time.sleeps).containsExactly(INTERVAL, INTERVAL);
    }

    @Test
    @DisplayName("직전 요청 뒤 이미 흐른 시간만큼은 덜 기다린다")
    void cargoQuery_WhenTimePassedSinceLastRequest_WaitOnlyRemainder() {
        // given
        expectCargoPage(0, cargoPage(0, 1));
        expectCargoPage(0, cargoPage(0, 1));
        LeaguepediaClient client = client(USERNAME, PASSWORD);
        client.cargoQuery(SCOREBOARD);
        time.advance(Duration.ofSeconds(5));

        // when
        client.cargoQuery(SCOREBOARD);

        // then
        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(7));
    }

    @ParameterizedTest
    @CsvSource({"ratelimited", "maxlag"})
    @DisplayName("ratelimited·maxlag면 백오프만큼 쉬고 같은 페이지를 다시 요청한다")
    void cargoQuery_WhenThrottledOnce_BackOffAndRetrySamePage(String code) {
        // given
        expectCargoPage(0, errorBody(code));
        expectCargoPage(0, cargoPage(0, 2));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        List<Map<String, String>> rows = client.cargoQuery(SCOREBOARD);

        // then
        assertThat(rows).hasSize(2);
        assertThat(time.sleeps).containsExactly(BACKOFF);
        server.verify();
    }

    @Test
    @DisplayName("백오프는 재시도마다 두 배로 늘고, 최대 횟수를 넘기면 예외를 던진다")
    void cargoQuery_WhenThrottledBeyondMaxRetries_DoubleBackOffThenThrow() {
        // given
        server.expect(ExpectedCount.times(MAX_RETRIES + 1), requestTo(Matchers.startsWith(API)))
                .andRespond(withSuccess(errorBody("ratelimited"), MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when & then
        assertThatThrownBy(() -> client.cargoQuery(SCOREBOARD))
                .isInstanceOf(LeaguepediaException.class)
                .hasMessageContaining("ratelimited");
        assertThat(time.sleeps).containsExactly(BACKOFF, BACKOFF.multipliedBy(2), BACKOFF.multipliedBy(4));
        server.verify();
    }

    @Test
    @DisplayName("로그인 요청도 간격을 지킨다")
    void login_WhenTokenThenLogin_WaitIntervalBeforePost() {
        // given
        expectLoginToken();
        server.expect(requestTo(API))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"login":{"result":"Success"}}
                        """, MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        client.login();

        // then
        assertThat(time.sleeps).containsExactly(INTERVAL);
    }

    @Test
    @DisplayName("조인이 있는 cargoquery는 join_on을 함께 보낸다")
    void cargoQuery_WhenJoinGiven_SendJoinOn() {
        // given
        server.expect(requestTo(Matchers.startsWith(API)))
                .andExpect(queryParam("tables", "PlayerImages%3DPI,%20Tournaments%3DT"))
                .andExpect(queryParam("join_on", "PI.Tournament%3DT.OverviewPage"))
                .andRespond(withSuccess(cargoPage(0, 1), MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        client.cargoQuery(new CargoQuery("PlayerImages=PI, Tournaments=T", "PI.Link", "PI.IsProfileImage = '1'",
                "T.DateStart DESC", "PI.Tournament=T.OverviewPage"));

        // then
        server.verify();
    }

    @Test
    @DisplayName("조인이 없으면 join_on을 보내지 않는다")
    void cargoQuery_WhenNoJoin_OmitJoinOn() {
        // given
        server.expect(requestTo(Matchers.not(Matchers.containsString("join_on"))))
                .andRespond(withSuccess(cargoPage(0, 1), MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        client.cargoQuery(SCOREBOARD);

        // then
        server.verify();
    }

    @Test
    @DisplayName("사진 파일 URL을 imageinfo로 받고, 위키가 정규화한 제목을 요청한 파일 이름에 맞춰 돌려준다")
    void imageUrls_WhenFilesExist_MapRequestedNameToUrl() {
        // given
        server.expect(requestTo(Matchers.startsWith(API)))
                .andExpect(queryParam("action", "query"))
                .andExpect(queryParam("prop", "imageinfo"))
                .andExpect(queryParam("iiprop", "url"))
                .andExpect(queryParam("titles", "File:T1_Faker_2026.png%7CFile:Missing.png"))
                .andRespond(withSuccess("""
                        {"query":{
                          "normalized":[{"from":"File:T1_Faker_2026.png","to":"File:T1 Faker 2026.png"}],
                          "pages":{
                            "123":{"title":"File:T1 Faker 2026.png","imageinfo":[{"url":"https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/a/ab/T1_Faker_2026.png"}]},
                            "-1":{"title":"File:Missing.png","missing":""}
                          }}}
                        """, MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        Map<String, String> urls = client.imageUrls(List.of("T1_Faker_2026.png", "Missing.png"));

        // then
        assertThat(urls).containsExactly(Map.entry("T1_Faker_2026.png",
                "https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/a/ab/T1_Faker_2026.png"));
    }

    @Test
    @DisplayName("사진 파일은 한 번에 50개씩 나눠 요청한다")
    void imageUrls_WhenMoreThanFiftyFiles_RequestInBatches() {
        // given
        List<String> files = IntStream.range(0, 51).mapToObj(index -> "P" + index + ".png").toList();
        server.expect(ExpectedCount.times(2), requestTo(Matchers.startsWith(API)))
                .andRespond(withSuccess("""
                        {"query":{"pages":{}}}
                        """, MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when
        client.imageUrls(files);

        // then
        server.verify();
    }

    @Test
    @DisplayName("요청할 사진이 없으면 요청하지 않는다")
    void imageUrls_WhenNoFile_SendNothing() {
        // when
        Map<String, String> urls = client(USERNAME, PASSWORD).imageUrls(List.of());

        // then
        assertThat(urls).isEmpty();
        server.verify();
    }

    private static String errorBody(String code) {
        return """
                {"error":{"code":"%s","info":"slow down"}}
                """.formatted(code);
    }

    private void expectCargoPage(int offset, String body) {
        server.expect(requestTo(Matchers.startsWith(API)))
                .andExpect(queryParam("action", "cargoquery"))
                .andExpect(queryParam("offset", String.valueOf(offset)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String cargoPage(int firstIndex, int size) {
        String rows = IntStream.range(firstIndex, firstIndex + size)
                .mapToObj(index -> """
                        {"title":{"GameId":"game-%d","Link":"Faker","Champion":"Ahri","DateTime UTC":"2026-10-18 19:48:00"}}
                        """.formatted(index).strip())
                .collect(Collectors.joining(","));
        return "{\"cargoquery\":[" + rows + "]}";
    }

    private void expectLoginToken() {
        server.expect(requestTo(Matchers.startsWith(API)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("action", "query"))
                .andExpect(queryParam("meta", "tokens"))
                .andExpect(queryParam("type", "login"))
                .andExpect(queryParam("format", "json"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess("""
                        {"query":{"tokens":{"logintoken":"token+\\\\"}}}
                        """, MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.SET_COOKIE, "lolwiki_session=abc; path=/; secure; HttpOnly"));
    }

    private LeaguepediaClient client(String username, String password) {
        return new LeaguepediaClient(builder, properties(username, password), time, time::sleep);
    }

    private static LeaguepediaProperties properties(String username, String password) {
        return new LeaguepediaProperties(URI.create(BASE_URL), username, password, USER_AGENT,
                INTERVAL, BACKOFF, MAX_RETRIES);
    }

    /** 잠들면 그만큼 시간이 흐르는 가짜 시계. 기다린 시간을 기록한다. */
    private static class FakeTime extends Clock {

        private Instant now = Instant.parse("2026-10-22T00:00:00Z");
        private final List<Duration> sleeps = new ArrayList<>();

        void sleep(Duration duration) {
            sleeps.add(duration);
            advance(duration);
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
