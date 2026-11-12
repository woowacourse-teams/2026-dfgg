package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.AugmentDescriptionTemplate;
import dfgg.domain.language.Language;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 한 언어의 게임 문자열 파일({@code lol.stringtable.json}) 중 필요한 키만 남긴 것. 키는 소문자다.
 * <p>
 * 파일이 35MB, 약 14만 항목이라 통째로 올리면 힙을 크게 쓴다. 읽으면서 필요한 키만 남긴다.
 */
public final class GameStrings {

    private final Language language;
    private final Map<String, String> strings;

    private GameStrings(Language language, Map<String, String> strings) {
        this.language = language;
        this.strings = strings;
    }

    public static GameStrings empty(Language language) {
        return new GameStrings(language, Map.of());
    }

    public static GameStrings read(Language language, JsonParser parser, Set<String> keys) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new AugmentDataRejectedException("문자열 파일이 객체가 아닙니다.");
        }
        Map<String, String> found = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            if (parser.nextToken() == JsonToken.START_OBJECT && field.equals("entries")) {
                found = entriesOf(parser, keys);
                continue;
            }
            parser.skipChildren();
        }
        if (found == null) {
            throw new AugmentDataRejectedException("문자열 파일에 entries가 없습니다.");
        }
        return new GameStrings(language, Map.copyOf(found));
    }

    private static Map<String, String> entriesOf(JsonParser parser, Set<String> keys) throws IOException {
        Map<String, String> found = new HashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String key = parser.currentName().toLowerCase(Locale.ROOT);
            if (parser.nextToken() == JsonToken.VALUE_STRING && keys.contains(key)) {
                found.put(key, parser.getText());
                continue;
            }
            parser.skipChildren();
        }
        return found;
    }

    public GameStrings merge(GameStrings other) {
        Map<String, String> merged = new HashMap<>(strings);
        merged.putAll(other.strings);
        return new GameStrings(language, Map.copyOf(merged));
    }

    /** 남긴 문장들이 끼워 넣는 키. 이 키들을 찾으려고 파일을 다시 받는다. */
    public Set<String> includedKeys() {
        return strings.values().stream()
                .flatMap(text -> new AugmentDescriptionTemplate(language, text).includedKeys().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<AugmentDescriptionTemplate> templateOf(String key) {
        return Optional.ofNullable(strings.get(key)).map(text -> new AugmentDescriptionTemplate(language, text));
    }

    public Language language() {
        return language;
    }

    public Map<String, String> asMap() {
        return strings;
    }
}
