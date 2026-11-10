package dfgg.domain.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.common.exception.InvalidLanguageException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    @ParameterizedTest(name = "lang=[{0}] → {1}")
    @CsvSource({
            "ko_KR, KO_KR",
            "ko-KR, KO_KR",
            "KO_kr, KO_KR",
            "en_US, EN_US",
            "en-US, EN_US",
            "EN_us, EN_US"
    })
    @DisplayName("게임 언어 값은 밑줄·하이픈 표기와 대소문자를 가리지 않고 해석한다")
    void fromGameLanguage_WhenSupported_ReturnsLanguage(String lang, Language expected) {
        // when
        Language language = Language.fromGameLanguage(lang);

        // then
        assertThat(language).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ko", "en", "ja_JP", "ko_KR_x"})
    @DisplayName("지원하지 않는 게임 언어 값은 거부한다")
    void fromGameLanguage_WhenUnsupported_Throws(String lang) {
        // when & then
        assertThatThrownBy(() -> Language.fromGameLanguage(lang))
                .isInstanceOf(InvalidLanguageException.class);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "KO_KR, ko_KR",
            "EN_US, en_US"
    })
    @DisplayName("게임 언어 값은 밑줄 표기로 낸다")
    void gameLanguage_ReturnsUnderscoreTag(Language language, String expected) {
        // when & then
        assertThat(language.gameLanguage()).isEqualTo(expected);
    }

    @Test
    @DisplayName("게임 언어가 있으면 Accept-Language보다 먼저 쓴다")
    void resolve_WhenGameLanguageGiven_PrefersItOverAcceptLanguage() {
        // when
        Language language = Language.resolve("en_US", "ko");

        // then
        assertThat(language).isEqualTo(Language.EN_US);
    }

    @Test
    @DisplayName("게임 언어가 없으면 Accept-Language로 고른다")
    void resolve_WhenGameLanguageAbsent_UsesAcceptLanguage() {
        // when
        Language language = Language.resolve(null, "ko");

        // then
        assertThat(language).isEqualTo(Language.KO_KR);
    }

    @Test
    @DisplayName("게임 언어가 지원하지 않는 값이면 Accept-Language로 대신하지 않고 거부한다")
    void resolve_WhenGameLanguageUnsupported_Throws() {
        // when & then
        assertThatThrownBy(() -> Language.resolve("ja_JP", "ko"))
                .isInstanceOf(InvalidLanguageException.class);
    }

    @Test
    @DisplayName("대체 없이 고르면 요청한 언어의 이름을 준다")
    void find_WhenNameExists_ReturnsNameInLanguage() {
        // given
        Map<String, String> names = Map.of("ko-KR", "손오공", "en-US", "Wukong");

        // when
        Optional<String> name = Language.EN_US.find(names);

        // then
        assertThat(name).contains("Wukong");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("대체 없이 고르면 요청한 언어의 이름이 없거나 비었을 때 한글로 대신하지 않는다")
    void find_WhenNameMissingOrBlank_ReturnsEmpty(String englishName) {
        // given
        Map<String, String> names = new HashMap<>();
        names.put("ko-KR", "손오공");
        names.put("en-US", englishName);

        // when
        Optional<String> name = Language.EN_US.find(names);

        // then
        assertThat(name).isEmpty();
    }
}
