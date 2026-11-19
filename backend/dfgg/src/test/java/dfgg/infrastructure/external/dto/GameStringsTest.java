package dfgg.infrastructure.external.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.language.Language;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GameStringsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("문자열 파일에서 요청한 키만 남긴다")
    void read_KeepsRequestedKeysOnly() throws Exception {
        // given
        String file = """
                {"entries": {"cherry_bluntforce_summary": "공격력이 증가합니다.",
                             "generatedtip_unrelated_entry": "관계없는 문장"},
                 "version": "16.22"}
                """;

        // when
        GameStrings strings = read(file, Set.of("cherry_bluntforce_summary"));

        // then
        assertThat(strings.asMap()).isEqualTo(Map.of("cherry_bluntforce_summary", "공격력이 증가합니다."));
    }

    @Test
    @DisplayName("파일의 키가 대문자를 섞어도 소문자로 찾는다")
    void read_MatchesKeysCaseInsensitively() throws Exception {
        // given
        String file = """
                {"entries": {"Kiwi_Augment_Totentanz_Summoner_Name": "죽음과 함께하는 춤"}}
                """;

        // when
        GameStrings strings = read(file, Set.of("kiwi_augment_totentanz_summoner_name"));

        // then
        assertThat(strings.asMap()).containsEntry("kiwi_augment_totentanz_summoner_name", "죽음과 함께하는 춤");
    }

    @Test
    @DisplayName("문자열이 아닌 값은 건너뛴다")
    void read_SkipsNonStringValues() throws Exception {
        // given
        String file = """
                {"entries": {"a": {"nested": "x"}, "b": "문장"}}
                """;

        // when
        GameStrings strings = read(file, Set.of("a", "b"));

        // then
        assertThat(strings.asMap()).isEqualTo(Map.of("b", "문장"));
    }

    @Test
    @DisplayName("entries가 없으면 거부한다")
    void read_WhenEntriesMissing_Rejects() {
        // when & then
        assertThatThrownBy(() -> read("{\"version\": \"16.22\"}", Set.of("a")))
                .isInstanceOf(AugmentDataRejectedException.class);
    }

    private GameStrings read(String file, Set<String> keys) throws Exception {
        try (JsonParser parser = JSON.createParser(file)) {
            return GameStrings.read(Language.KO_KR, parser, keys);
        }
    }
}
