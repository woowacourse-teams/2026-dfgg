package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.infrastructure.external.dto.CommunityDragonDescriptions;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** fixture는 16.22 실제 파일에서 증강 1007, 1067, 1154, 3079에 필요한 항목만 잘라 냈다. */
class CommunityDragonClientDescriptionsTest {

    private static final String GAME = "https://raw.communitydragon.org/16.22/game/";
    private static final String KIWI = GAME + "maps/modespecificdata/kiwi.bin.json";
    private static final String KOREAN = GAME + "ko_kr/data/menu/en_us/lol.stringtable.json";
    private static final String ENGLISH = GAME + "en_us/data/menu/en_us/lol.stringtable.json";

    private MockRestServiceServer server;
    private CommunityDragonClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CommunityDragonClient(builder);
    }

    @Test
    @DisplayName("증강 정의와 한글·영어 문자열로 설명을 만든다")
    void getAugmentDescriptions_BuildsKoreanAndEnglish() {
        // given
        expectAllFiles();

        // when
        CommunityDragonDescriptions descriptions = client.getAugmentDescriptions("16.22");

        // then
        assertThat(descriptions.descriptionOf(1007))
                .isEqualTo(Map.of("ko-KR", "공격력이 20% 증가합니다.", "en-US", "Gain 20% Attack Damage."));
    }

    @Test
    @DisplayName("끼워 넣을 문장이 새로 필요하면 문자열 파일을 다시 받아 찾는다")
    void getAugmentDescriptions_WhenIncludeFound_FetchesStringsAgain() {
        // given
        expectAllFiles();

        // when
        CommunityDragonDescriptions descriptions = client.getAugmentDescriptions("16.22");

        // then
        assertThat(descriptions.descriptionOf(3079)).containsEntry("ko-KR", "소환사 주문이 죽음과 함께하는 춤으로 변경됩니다.\n\n"
                + "죽음과 함께하는 춤: 전방으로 순간적으로 이동하여 불사 상태가 됩니다. 5초 이내에 처치 관여를 달성하지 않으면\n사망합니다.");
        assertThat(descriptions.descriptionOf(3079)).containsEntry("en-US", "Replace a Summoner Spell with A Dance with Death.\n\n"
                + "A Dance with Death: Blink forward and become Unkillable. Get a takedown within 5 seconds or else\nYOU DIE.");
        server.verify();
    }

    @Test
    @DisplayName("스킬 증강의 스킬 이름 자리는 일반 표현으로 바꾼다")
    void getAugmentDescriptions_WhenAbilityAugment_UsesGenericSkill() {
        // given
        expectAllFiles();

        // when
        CommunityDragonDescriptions descriptions = client.getAugmentDescriptions("16.22");

        // then
        assertThat(descriptions.descriptionOf(1067))
                .isEqualTo(Map.of("ko-KR", "해당 스킬 사용 시 체력을 회복합니다.", "en-US", "Using [Ability] restores Health."));
    }

    @Test
    @DisplayName("값을 찾지 못한 증강은 설명이 없다")
    void descriptionOf_WhenPlaceholderUndefined_IsEmpty() {
        // given
        expectAllFiles();

        // when
        CommunityDragonDescriptions descriptions = client.getAugmentDescriptions("16.22");

        // then
        assertThat(descriptions.descriptionOf(1154)).isEmpty();
    }

    @Test
    @DisplayName("정의에 없는 증강은 설명이 없다")
    void descriptionOf_WhenAugmentUnknown_IsEmpty() {
        // given
        expectAllFiles();

        // when
        CommunityDragonDescriptions descriptions = client.getAugmentDescriptions("16.22");

        // then
        assertThat(descriptions.descriptionOf(9999)).isEmpty();
    }

    @Test
    @DisplayName("증강 정의 파일을 받지 못하면 거부한다")
    void getAugmentDescriptions_WhenDefinitionDownloadFails_Rejects() {
        // given
        server.expect(requestTo(KIWI)).andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.getAugmentDescriptions("16.22"))
                .isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("문자열 파일을 읽지 못하면 거부한다")
    void getAugmentDescriptions_WhenStringsMalformed_Rejects() {
        // given
        server.expect(requestTo(KIWI)).andRespond(withSuccess(fixture("kiwi.bin.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(KOREAN)).andRespond(withSuccess("{\"entries\": {\"a\": ", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.getAugmentDescriptions("16.22"))
                .isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("문자열 파일에 entries가 없으면 거부한다")
    void getAugmentDescriptions_WhenEntriesMissing_Rejects() {
        // given
        server.expect(requestTo(KIWI)).andRespond(withSuccess(fixture("kiwi.bin.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(KOREAN)).andRespond(withSuccess("{\"version\": \"16.22\"}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.getAugmentDescriptions("16.22"))
                .isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("증강 정의 파일이 객체가 아니면 거부한다")
    void getAugmentDescriptions_WhenDefinitionNotObject_Rejects() {
        // given
        server.expect(requestTo(KIWI)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.getAugmentDescriptions("16.22"))
                .isInstanceOf(AugmentDataRejectedException.class);
    }

    /** 3079가 끼워 넣는 문장은 파일에서 설명 문장보다 뒤에 있어 두 번째로 받을 때 찾는다. */
    private void expectAllFiles() {
        server.expect(requestTo(KIWI)).andRespond(withSuccess(fixture("kiwi.bin.json"), MediaType.APPLICATION_JSON));
        server.expect(times(2), requestTo(KOREAN))
                .andRespond(withSuccess(fixture("ko_kr.stringtable.json"), MediaType.APPLICATION_JSON));
        server.expect(times(2), requestTo(ENGLISH))
                .andRespond(withSuccess(fixture("en_us.stringtable.json"), MediaType.APPLICATION_JSON));
    }

    private byte[] fixture(String file) {
        try (InputStream input = getClass().getResourceAsStream("/communitydragon/16.22/" + file)) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
