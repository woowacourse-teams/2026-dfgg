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

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
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
                        {"error":{"code":"maxlag","info":"Waiting for a database server"}}
                        """, MediaType.APPLICATION_JSON));
        LeaguepediaClient client = client(USERNAME, PASSWORD);

        // when & then
        assertThatThrownBy(client::login)
                .isInstanceOf(LeaguepediaException.class)
                .hasMessageContaining("maxlag");
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
        return new LeaguepediaClient(builder, properties(username, password));
    }

    private static LeaguepediaProperties properties(String username, String password) {
        return new LeaguepediaProperties(URI.create(BASE_URL), username, password, USER_AGENT);
    }
}
