package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class DataDragonLocalizedItemsTest {

    private static final String ITEM_URL = "https://ddragon.leagueoflegends.com/cdn/16.18.1/data/";
    private static final String ENGLISH_ITEMS = """
            {"version":"16.18.1","data":{"1036":{"name":"Long Sword"}}}
            """;
    private static final String KOREAN_ITEMS = """
            {"version":"16.18.1","data":{"1036":{
                "name":"롱소드","from":[],"into":["3133"],"tags":["Damage"],
                "maps":{"11":true},"consumed":false,"depth":1,
                "gold":{"base":350,"total":350,"purchasable":true},
                "image":{"full":"1036.png"},"inStore":true,"hideFromAll":false,
                "stacks":1,"stats":{"FlatPhysicalDamageMod":10.0}
            }}}
            """;

    private MockRestServiceServer server;
    private DataDragonClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DataDragonClient(builder);
    }

    @Test
    void 지정한_빌드의_한글_카탈로그와_영문_이름을_조회한다() {
        expectItems("en_US", ENGLISH_ITEMS);
        expectItems("ko_KR", KOREAN_ITEMS);

        ItemResponse response = client.getLocalizedItems("16.18.1");

        assertThat(response.version()).isEqualTo("16.18");
        assertThat(response.dataVersion()).isEqualTo("16.18.1");
        assertThat(response.localizedName("1036"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("ko-KR", "롱소드", "en-US", "Long Sword"));
        ItemData item = response.data().get("1036");
        assertThat(item.from()).isEmpty();
        assertThat(item.into()).containsExactly("3133");
        assertThat(item.tags()).containsExactly("Damage");
        assertThat(item.maps()).containsEntry("11", true);
        assertThat(item.consumed()).isFalse();
        assertThat(item.depth()).isEqualTo(1);
        assertThat(item.gold()).isEqualTo(new ItemData.Gold(350, 350, true));
        assertThat(item.image().full()).isEqualTo("1036.png");
        assertThat(item.inStore()).isTrue();
        assertThat(item.hideFromAll()).isFalse();
        assertThat(item.stacks()).isEqualTo(1);
        assertThat(item.stats()).containsEntry("FlatPhysicalDamageMod", 10.0);
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"16.18", "latest", "16.18.1/other"})
    void 잘못된_빌드_형식은_HTTP_요청_전에_거부한다(String version) {
        assertThatThrownBy(() -> client.getLocalizedItems(version))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_US", "ko_KR"})
    void 어느_언어든_요청한_빌드와_다르면_거부한다(String locale) {
        if (locale.equals("ko_KR")) {
            expectItems("en_US", ENGLISH_ITEMS);
        }
        expectItems(locale, ENGLISH_ITEMS.replace("16.18.1", "16.19.1"));

        assertThatThrownBy(() -> client.getLocalizedItems("16.18.1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("응답 버전");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"data\":{}}", "{\"data\":null}"})
    void 한글_아이템_목록이_없으면_거부한다(String body) {
        expectItems("en_US", ENGLISH_ITEMS);
        expectItems("ko_KR", body);

        assertThatThrownBy(() -> client.getLocalizedItems("16.18.1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("비어 있습니다");
        server.verify();
    }

    @Test
    void 한글_응답_본문이_없으면_거부한다() {
        expectItems("en_US", ENGLISH_ITEMS);
        server.expect(requestTo(ITEM_URL + "ko_KR/item.json")).andRespond(withNoContent());

        assertThatThrownBy(() -> client.getLocalizedItems("16.18.1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("비어 있습니다");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"name\":\" \"}"})
    void 한글_아이템_이름이_없으면_거부한다(String item) {
        expectItems("en_US", ENGLISH_ITEMS);
        expectItems("ko_KR", "{\"version\":\"16.18.1\",\"data\":{\"1036\":" + item + "}}");

        assertThatThrownBy(() -> client.getLocalizedItems("16.18.1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("한글 이름");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"1001\":{\"name\":\"Boots\"}}", "{\"1036\":null}",
            "{\"1036\":{}}", "{\"1036\":{\"name\":\" \"}}"})
    void 한글_아이템에_대응하는_영문_이름이_없으면_거부한다(String data) {
        expectItems("en_US", "{\"version\":\"16.18.1\",\"data\":" + data + "}");
        expectItems("ko_KR", KOREAN_ITEMS);

        assertThatThrownBy(() -> client.getLocalizedItems("16.18.1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("영문 이름");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_US", "ko_KR"})
    void HTTP_오류를_다른_버전으로_대체하지_않고_전파한다(String locale) {
        if (locale.equals("ko_KR")) {
            expectItems("en_US", ENGLISH_ITEMS);
        }
        server.expect(requestTo(ITEM_URL + locale + "/item.json")).andRespond(withServerError());

        assertThatThrownBy(() -> client.getLocalizedItems("16.18.1"))
                .isInstanceOf(HttpServerErrorException.class);
        server.verify();
    }

    private void expectItems(String locale, String body) {
        server.expect(requestTo(ITEM_URL + locale + "/item.json"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
