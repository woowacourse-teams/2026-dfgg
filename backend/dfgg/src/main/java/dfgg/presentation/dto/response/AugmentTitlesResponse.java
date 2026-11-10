package dfgg.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dfgg.domain.language.Language;
import dfgg.infrastructure.image.AugmentTitleStyle;
import dfgg.presentation.dto.AugmentTitleDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 한 패치·언어의 증강 제목 견본 전체.
 *
 * @param patch    데이터 기준 패치
 * @param lang     견본 언어. 받은 표기와 상관없이 {@code ko_KR}·{@code en_US}
 * @param font     견본을 그린 폰트. 앱이 규격이 바뀌었는지 확인하는 데 쓴다
 * @param fontSize 글자 크기(px)
 * @param padding  좌우 여백(px)
 * @param images   증강 ID 순 견본
 */
public record AugmentTitlesResponse(
        String patch,
        String lang,
        String font,
        int fontSize,
        int padding,
        List<AugmentTitleDto> images
) {

    /** ETag 길이. SHA-256 앞 16바이트(128비트)면 충돌을 걱정할 일이 없다. */
    private static final int ETAG_BYTES = 16;

    public static AugmentTitlesResponse of(String patch, Language language, AugmentTitleStyle style,
                                           List<AugmentTitleDto> images) {
        return new AugmentTitlesResponse(patch, language.gameLanguage(), style.font(), style.fontSize(),
                style.padding(), images);
    }

    /**
     * 그림까지 포함한 응답 내용의 해시로 만든 강한 ETag.
     * <p>
     * 그리는 방식이 바뀌어도 규격 버전을 따로 올리지 않아도 ETag가 바뀐다. 앱은 이 값으로 다시 받을지 정한다.
     */
    @JsonIgnore
    public String etag() {
        MessageDigest digest = sha256();
        update(digest, patch, lang, font, String.valueOf(fontSize), String.valueOf(padding));
        images.forEach(image -> update(digest, String.valueOf(image.augmentId()), image.name(), image.png()));
        return "\"" + HexFormat.of().formatHex(digest.digest(), 0, ETAG_BYTES) + "\"";
    }

    private static void update(MessageDigest digest, String... fields) {
        for (String field : fields) {
            digest.update((field + '\n').getBytes(StandardCharsets.UTF_8));
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", exception);
        }
    }
}
