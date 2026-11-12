package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentRarity;
import dfgg.domain.language.Language;
import dfgg.infrastructure.external.dto.CommunityDragonAugments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CommunityDragonClientTest {

    private static final String DATA = "https://raw.communitydragon.org/16.21/plugins/rcp-be-lol-game-data/global/";
    private static final String ICON_PATH = "/lol-game-data/assets/ASSETS/UX/Kiwi/Augments/Icons/DiveBomber_small.png";
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1};

    private MockRestServiceServer server;
    private CommunityDragonClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CommunityDragonClient(builder);
    }

    @Test
    @DisplayName("패치 폴더의 한글·영어 파일을 합쳐 증강을 만든다")
    void getAugments_MergesKoreanAndEnglish() {
        // given
        expectAugments(augment(1373, "축소 엔진", "kGold"), augment(1373, "Shrink Engine", "kGold"));

        // when
        Augment augment = client.getAugments("16.21").toAugment("16.21", 1373).orElseThrow();

        // then
        assertThat(augment.nameIn(Language.KO_KR)).isEqualTo("축소 엔진");
        assertThat(augment.nameIn(Language.EN_US)).isEqualTo("Shrink Engine");
        assertThat(augment.rarity()).isEqualTo(AugmentRarity.GOLD);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kSilver:SILVER", "kGold:GOLD", "kPrismatic:PRISMATIC"})
    @DisplayName("등급 값을 증강 등급으로 바꾼다")
    void getAugments_MapsRarity(String mapping) {
        // given
        String[] rarity = mapping.split(":");
        expectAugments(augment(1, "가", rarity[0]), augment(1, "A", rarity[0]));

        // when
        Augment augment = client.getAugments("16.21").toAugment("16.21", 1).orElseThrow();

        // then
        assertThat(augment.rarity()).isEqualTo(AugmentRarity.valueOf(rarity[1]));
    }

    @Test
    @DisplayName("모르는 등급의 증강은 연결하지 않는다")
    void toAugment_WhenRarityUnknown_IsEmpty() {
        // given
        expectAugments(augment(1, "가", "kEventChoice"), augment(1, "A", "kEventChoice"));

        // when
        CommunityDragonAugments augments = client.getAugments("16.21");

        // then
        assertThat(augments.toAugment("16.21", 1)).isEmpty();
    }

    @Test
    @DisplayName("이름이 비었거나 한쪽 언어에 없는 증강은 연결하지 않는다")
    void toAugment_WhenNameMissing_IsEmpty() {
        // given
        expectAugments(augment(1, "", "kGold") + "," + augment(2, "나", "kGold"), augment(1, "A", "kGold"));

        // when
        CommunityDragonAugments augments = client.getAugments("16.21");

        // then
        assertThat(augments.toAugment("16.21", 1)).isEmpty();
        assertThat(augments.toAugment("16.21", 2)).isEmpty();
        assertThat(augments.toAugment("16.21", 3)).isEmpty();
    }

    @Test
    @DisplayName("영어 이름이 비면 연결하지 않는다")
    void toAugment_WhenEnglishNameBlank_IsEmpty() {
        // given
        expectAugments(augment(1, "가", "kGold"), augment(1, " ", "kGold"));

        // when
        CommunityDragonAugments augments = client.getAugments("16.21");

        // then
        assertThat(augments.toAugment("16.21", 1)).isEmpty();
    }

    @Test
    @DisplayName("이름 필드가 없으면 연결하지 않는다")
    void toAugment_WhenNameFieldAbsent_IsEmpty() {
        // given
        String withoutName = """
                {"id": 1, "rarity": "kGold"}
                """;
        expectAugments(withoutName, augment(1, "A", "kGold"));

        // when
        CommunityDragonAugments augments = client.getAugments("16.21");

        // then
        assertThat(augments.toAugment("16.21", 1)).isEmpty();
    }

    @Test
    @DisplayName("파일을 받지 못하면 거부한다")
    void getAugments_WhenDownloadFails_Rejects() {
        // given
        server.expect(requestTo(DATA + "ko_kr/v1/cherry-augments.json")).andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.getAugments("16.21")).isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("JSON을 읽지 못하면 거부한다")
    void getAugments_WhenJsonMalformed_Rejects() {
        // given
        server.expect(requestTo(DATA + "ko_kr/v1/cherry-augments.json"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.getAugments("16.21")).isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("목록에 null이 있으면 거부한다")
    void getAugments_WhenListContainsNull_Rejects() {
        // given
        server.expect(requestTo(DATA + "ko_kr/v1/cherry-augments.json"))
                .andRespond(withSuccess("[null]", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.getAugments("16.21")).isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("같은 ID가 두 번 있으면 거부한다")
    void getAugments_WhenIdDuplicated_Rejects() {
        // given
        expectAugments(augment(1, "가", "kGold") + "," + augment(1, "나", "kGold"), augment(1, "A", "kGold"));

        // when & then
        assertThatThrownBy(() -> client.getAugments("16.21")).isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("아이콘은 소문자 경로의 패치 폴더에서 받는다")
    void getAugmentIcon_RequestsLowerCasePathInPatchFolder() {
        // given
        server.expect(requestTo(DATA + "default/assets/ux/kiwi/augments/icons/divebomber_small.png"))
                .andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        // when
        byte[] icon = client.getAugmentIcon("16.21", ICON_PATH);

        // then
        assertThat(icon).isEqualTo(PNG);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/lol-game-data/assets/../secret.png",
            "/other/assets/ASSETS/icon.png",
            "/lol-game-data/assets/ASSETS/UX/icon.jpg",
            "https://example.com/icon.png",
            "https://example.com/lol-game-data/assets/ASSETS/icon.png",
            "/lol-game-data/assets/ASSETS/icon.png/../../secret.png"
    })
    @DisplayName("정해진 경로 밖의 아이콘은 요청하지 않고 거부한다")
    void getAugmentIcon_WhenPathOutsideAssets_Rejects(String iconPath) {
        // when & then
        assertThatThrownBy(() -> client.getAugmentIcon("16.21", iconPath))
                .isInstanceOf(AugmentDataRejectedException.class);
        server.verify();
    }

    @Test
    @DisplayName("PNG가 아니면 거부한다")
    void getAugmentIcon_WhenNotPng_Rejects() {
        // given
        server.expect(requestTo(DATA + "default/assets/ux/kiwi/augments/icons/divebomber_small.png"))
                .andRespond(withSuccess("<html>", MediaType.TEXT_HTML));

        // when & then
        assertThatThrownBy(() -> client.getAugmentIcon("16.21", ICON_PATH))
                .isInstanceOf(AugmentDataRejectedException.class);
    }

    private void expectAugments(String koreanBody, String englishBody) {
        server.expect(requestTo(DATA + "ko_kr/v1/cherry-augments.json"))
                .andRespond(withSuccess("[" + koreanBody + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA + "default/v1/cherry-augments.json"))
                .andRespond(withSuccess("[" + englishBody + "]", MediaType.APPLICATION_JSON));
    }

    private String augment(int id, String name, String rarity) {
        return """
                {"id": %d, "augmentNameId": "ARAM_X", "nameTRA": "%s", "simpleNameTRA": "", "rarity": "%s",
                 "augmentSmallIconPath": "%s"}
                """.formatted(id, name, rarity, ICON_PATH);
    }
}
