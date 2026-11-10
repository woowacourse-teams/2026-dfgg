package dfgg.presentation.dto.response;

import dfgg.domain.language.Language;
import dfgg.infrastructure.image.AugmentTitleStyle;
import dfgg.presentation.dto.AugmentTitleDto;
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

    public static AugmentTitlesResponse of(String patch, Language language, AugmentTitleStyle style,
                                           List<AugmentTitleDto> images) {
        return new AugmentTitlesResponse(patch, language.gameLanguage(), style.font(), style.fontSize(),
                style.padding(), images);
    }
}
