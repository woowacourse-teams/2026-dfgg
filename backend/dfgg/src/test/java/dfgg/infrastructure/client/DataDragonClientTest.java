package dfgg.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

public class DataDragonClientTest {

    private static final String BASE_URL = "https://ddragon.leagueoflegends.com";

    private MockRestServiceServer server;
    private DataDragonClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        server = MockRestServiceServer.bindTo(builder).build();
        client = new DataDragonClient(builder);
    }

    @Test
    void 버전_목록의_첫_번째_값을_최신_버전으로_반환한다() {
        // given
        server.expect(requestTo(BASE_URL + "/api/versions.json"))
                .andRespond(withSuccess("""
                        ["16.15.1", "16.14.1"]
                        """, MediaType.APPLICATION_JSON));
        // when
        String lastVersion = client.getLatestVersion();

        // then
        Assertions.assertThat(lastVersion).isEqualTo("16.15.1");
        server.verify();
    }

    @Test
    void API_호출에_실패하면_예외가_발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/api/versions.json"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(client::getLatestVersion)
                .isInstanceOf(HttpServerErrorException.class);

        server.verify();
    }

    @Test
    void 버전_목록이_비어있으면_예외가_발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/api/versions.json"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(client::getLatestVersion)
                .isInstanceOf(IllegalArgumentException.class);

        server.verify();
    }
}
