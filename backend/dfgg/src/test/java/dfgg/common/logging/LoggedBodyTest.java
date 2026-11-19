package dfgg.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggedBodyTest {

    private static final int MAX_BYTES = 8 * 1024;

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("JSON 본문은 중첩 객체로 남긴다")
    void of_WhenJson_KeepNestedObject() {
        // when
        LoggedBody body = LoggedBody.of(bytes("{\"queueId\":420,\"teams\":[{\"teamId\":100}]}"), MAX_BYTES);

        // then
        assertThat(body.logValue()).isEqualTo(Map.of("queueId", 420, "teams", List.of(Map.of("teamId", 100))));
        assertThat(body.isTruncated()).isFalse();
    }

    @Test
    @DisplayName("중첩 객체와 배열 안의 puuid도 앞 4자만 남긴다")
    void of_WhenPuuidNested_MaskEveryPuuid() {
        // when
        LoggedBody body = LoggedBody.of(
                bytes("{\"puuid\":\"Ab3xQwErTy\",\"participants\":[{\"puuid\":\"Zz9yLongValue\",\"teamId\":100}]}"),
                MAX_BYTES);

        // then
        assertThat(body.logValue()).isEqualTo(Map.of(
                "puuid", "Ab3x…",
                "participants", List.of(Map.of("puuid", "Zz9y…", "teamId", 100))));
    }

    @Test
    @DisplayName("상한을 넘으면 앞부분을 문자열로 자르고 잘렸다고 표시한다. 잘린 문자열 안의 puuid도 가린다")
    void of_WhenOverLimit_TruncateToStringAndMaskPuuid() {
        // given
        String json = "{\"puuid\":\"Ab3xQwErTy\",\"filler\":\"" + "a".repeat(100) + "\"}";

        // when
        LoggedBody body = LoggedBody.of(bytes(json), 40);

        // then
        assertThat(body.isTruncated()).isTrue();
        assertThat(body.logValue())
                .isInstanceOf(String.class)
                .asString()
                .startsWith("{\"puuid\":\"Ab3x…\"")
                .doesNotContain("QwErTy");
    }

    @Test
    @DisplayName("잘린 지점이 puuid 값 한가운데여도 원문이 남지 않는다")
    void of_WhenCutInsidePuuid_MaskPartialPuuid() {
        // when
        LoggedBody body = LoggedBody.of(bytes("{\"puuid\":\"Ab3xQwErTyUiOp\"}"), 16);

        // then
        assertThat(body.logValue()).asString().isEqualTo("{\"puuid\":\"Ab3x…");
    }

    @Test
    @DisplayName("JSON이 아니면 줄바꿈과 제어 문자를 ?로 바꾼 문자열로 남긴다")
    void of_WhenNotJson_ReplaceControlCharacters() {
        // when
        LoggedBody body = LoggedBody.of(bytes("hello\nfake-line\tend"), MAX_BYTES);

        // then
        assertThat(body.logValue()).isEqualTo("hello?fake-line?end");
    }

    @Test
    @DisplayName("빈 본문은 남길 것이 없다")
    void of_WhenEmpty_HaveNothingToLog() {
        // when
        LoggedBody body = LoggedBody.of(new byte[0], MAX_BYTES);

        // then
        assertThat(body.isEmpty()).isTrue();
    }
}
