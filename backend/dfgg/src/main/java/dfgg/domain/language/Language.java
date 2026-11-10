package dfgg.domain.language;

import dfgg.common.exception.InvalidLanguageException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 응답에 싣는 이름의 언어. 챔피언·아이템 이름은 {@code {"ko-KR": ..., "en-US": ...}}로 저장돼 있다.
 * <p>
 * 기본은 영어다. {@code ko}를 요청했을 때만 한국어로 낸다.
 */
public enum Language {

    KO_KR("ko-KR"),
    EN_US("en-US");

    /** 영문 이름이 비어 있을 때 대신 낼 언어. 한글 이름은 항상 저장돼 있다. */
    private static final Language FALLBACK = KO_KR;

    private final String tag;

    Language(String tag) {
        this.tag = tag;
    }

    /**
     * {@code Accept-Language}가 {@code ko}로 시작하면 한국어, 그 밖에는 헤더가 없어도 모두 영어다.
     * <p>
     * 클라이언트는 {@code ko}와 {@code en}만 보낸다. 우선순위(q)까지 해석할 필요가 없다.
     */
    public static Language fromAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage != null && acceptLanguage.trim().toLowerCase(Locale.ROOT).startsWith("ko")) {
            return KO_KR;
        }
        return EN_US;
    }

    /**
     * 게임 클라이언트 언어({@code ko_KR}, {@code en-US} 등). 밑줄·하이픈과 대소문자는 가리지 않는다.
     * <p>
     * 앱 언어와 달리 모르는 값을 영어로 넘기지 않는다. 게임 화면과 다른 언어 이름을 주면 앱이 잘못 판별한다.
     */
    public static Language fromGameLanguage(String lang) {
        String normalized = String.valueOf(lang).strip().replace('_', '-');
        return Arrays.stream(values())
                .filter(language -> language.tag.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new InvalidLanguageException(lang));
    }

    /** 게임 언어를 받았으면 그 언어, 없으면 {@code Accept-Language}로 고른다. */
    public static Language resolve(String gameLanguage, String acceptLanguage) {
        if (gameLanguage == null) {
            return fromAcceptLanguage(acceptLanguage);
        }
        return fromGameLanguage(gameLanguage);
    }

    /** 언어별 이름에서 이 언어의 이름을 고른다. 없으면 한글 이름으로 낸다. */
    public String pick(Map<String, String> names) {
        return find(names).orElseGet(() -> names.get(FALLBACK.tag));
    }

    /** 언어별 이름에서 이 언어의 이름만 고른다. 없거나 비어 있으면 다른 언어로 대신하지 않는다. */
    public Optional<String> find(Map<String, String> names) {
        return Optional.ofNullable(names.get(tag))
                .filter(name -> !name.isBlank());
    }
}
