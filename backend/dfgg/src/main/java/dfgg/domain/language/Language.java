package dfgg.domain.language;

import java.util.Locale;
import java.util.Map;

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
        if (acceptLanguage != null && acceptLanguage.trim().toLowerCase(Locale.ROOT).startsWith("en")) {
            return EN_US;
        }
        return KO_KR;
    }

    /** 언어별 이름에서 이 언어의 이름을 고른다. 없으면 한글 이름으로 낸다. */
    public String pick(Map<String, String> names) {
        String name = names.get(tag);
        if (name == null || name.isBlank()) {
            return names.get(FALLBACK.tag);
        }
        return name;
    }
}
