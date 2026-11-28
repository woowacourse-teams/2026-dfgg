package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dfgg.infrastructure.external.dto.SummonerSpellResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DataDragonSummonerSpellTest {

    private static final String BASE_URL = "https://ddragon.leagueoflegends.com";
    private MockRestServiceServer server;
    private DataDragonClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DataDragonClient(builder);
    }

    @Test
    @DisplayName("최신 빌드의 한글 소환사 주문에 같은 빌드의 영문 이름을 붙인다")
    void getSummonerSpells_WhenLatestVersion_MergeKoreanAndEnglishNames() {
        // given
        expectLatestVersion();
        server.expect(requestTo(BASE_URL + "/cdn/16.20.1/data/ko_KR/summoner.json"))
                .andRespond(withSuccess("""
                        {"data":{"SummonerFlash":{"id":"SummonerFlash","key":"4","name":"점멸",
                          "image":{"full":"SummonerFlash.png"},"modes":["CLASSIC","ARAM"]}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/cdn/16.20.1/data/en_US/summoner.json"))
                .andRespond(withSuccess("""
                        {"data":{"SummonerFlash":{"id":"SummonerFlash","key":"4","name":"Flash",
                          "image":{"full":"SummonerFlash.png"},"modes":["CLASSIC","ARAM"]}}}
                        """, MediaType.APPLICATION_JSON));

        // when
        SummonerSpellResponse response = client.getSummonerSpells();

        // then
        assertThat(response.version()).isEqualTo("16.20");
        assertThat(response.data().get("SummonerFlash").key()).isEqualTo("4");
        assertThat(response.data().get("SummonerFlash").image().full()).isEqualTo("SummonerFlash.png");
        assertThat(response.localizedName("SummonerFlash")).isEqualTo(Map.of("ko-KR", "점멸", "en-US", "Flash"));
        server.verify();
    }

    private void expectLatestVersion() {
        server.expect(requestTo(BASE_URL + "/api/versions.json"))
                .andRespond(withSuccess("[\"16.20.1\",\"16.19.1\"]", MediaType.APPLICATION_JSON));
    }
}
