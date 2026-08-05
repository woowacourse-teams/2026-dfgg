package dfgg.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dfgg.infrastructure.dto.ChampionResponse;
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
        assertThat(lastVersion).isEqualTo("16.15.1");
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
                .isInstanceOf(IllegalStateException.class);

        server.verify();
    }

    @Test
    void 챔피언_목록_응답_본문이_비어있으면_예외가_발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/api/versions.json"))
                .andRespond(withSuccess("""
                        ["16.15.1"]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE_URL + "/cdn/16.15.1/data/ko_KR/champion.json"))
                .andRespond(withNoContent());

        // when & then
        assertThatThrownBy(client::getChampions)
                .isInstanceOf(IllegalStateException.class);

        server.verify();
    }

    @Test
    void 최신_버전의_챔피언_목록을_조회한다() {
        // given
        server.expect(requestTo(BASE_URL + "/api/versions.json"))
                .andRespond(withSuccess("""
                        ["16.15.1"]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE_URL + "/cdn/16.15.1/data/ko_KR/champion.json"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "Aatrox": {
                              "key": "12",
                              "name": "아트록스"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        ChampionResponse response = client.getChampions();

        // then
        assertThat(response.data().get("Aatrox").name()).isEqualTo("아트록스");
        assertThat(response.data().get("Aatrox").key()).isEqualTo("12");

        server.verify();
    }
}
