package dfgg.domain.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTest {

    @ParameterizedTest(name = "Accept-Language=[{0}] → {1}")
    @CsvSource({
            "ko, KO_KR",
            "ko-KR, KO_KR",
            "'ko-KR,ko;q=0.9', KO_KR",
            "en, EN_US",
            "en-US, EN_US",
            "ja, EN_US"
    })
    @DisplayName("Accept-Language가 ko로 시작하면 한국어, 그 밖에는 영어를 고른다")
    void fromAcceptLanguage_WhenHeaderGiven_PicksKoreanOnlyForKo(String acceptLanguage, Language expected) {
        // when
        Language language = Language.fromAcceptLanguage(acceptLanguage);

        // then
        assertThat(language).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Accept-Language가 없으면 영어를 고른다")
    void fromAcceptLanguage_WhenHeaderAbsent_PicksEnglish(String acceptLanguage) {
        // when
        Language language = Language.fromAcceptLanguage(acceptLanguage);

        // then
        assertThat(language).isEqualTo(Language.EN_US);
    }

    @Test
    @DisplayName("언어별 이름 중 요청한 언어의 이름을 고른다")
    void pick_WhenNameExists_ReturnsNameInLanguage() {
        // given
        Map<String, String> names = Map.of("ko-KR", "손오공", "en-US", "Wukong");

        // when
        String name = Language.EN_US.pick(names);

        // then
        assertThat(name).isEqualTo("Wukong");
    }

    @Test
    @DisplayName("요청한 언어의 이름이 없으면 한글 이름을 고른다")
    void pick_WhenNameMissing_FallsBackToKorean() {
        // given
        Map<String, String> names = Map.of("ko-KR", "손오공");

        // when
        String name = Language.EN_US.pick(names);

        // then
        assertThat(name).isEqualTo("손오공");
    }

    @Test
    @DisplayName("요청한 언어의 이름이 비어 있으면 한글 이름을 고른다")
    void pick_WhenNameBlank_FallsBackToKorean() {
        // given
        Map<String, String> names = Map.of("ko-KR", "손오공", "en-US", " ");

        // when
        String name = Language.EN_US.pick(names);

        // then
        assertThat(name).isEqualTo("손오공");
    }
}
